package boxes

import collection._
import mutable.MultiMap
import util.WeakHashSet
import java.util.concurrent.locks.ReentrantLock
import scala.math.Numeric._
import Numeric.Implicits._
import scala.language.implicitConversions

object BoxImplicits {
  implicit def closureToPathViaOption[T](path : => Option[Var[T]]) = PathViaOption(path)
  implicit def closureToPath[T](path : =>Var[T]) = Path(path)
  implicit def closureToPathToOption[T](path : => Option[Var[Option[T]]]) = PathToOption(path)
  implicit def valueToVal[T](t:T) = Val(t)
}

class NumericVar[N](v: Var[N])(implicit n: Numeric[N]) {
  
  def from(min: N) = {v << n.max(min, v()); v}
  def to(max: N) = {v << n.min(max, v()); v}
  def from(min: Box[N, _]) = {v << n.max(min(), v()); v}
  def to(max: Box[N, _]) = {v << n.min(max(), v()); v}
  
  def clip(min: N, max: N) {
    val value = v()
    if (n.compare(value, min) < 0) v() = min else if (n.compare(value, max) > 0) v() = max
  }
}

object NumericVarImplicits {
  implicit def var2NumericVar[N: Numeric](v: Var[N]): NumericVar[N] = new NumericVar[N](v)
}

//Intents when writing to a box - covers the first write only, writes by reactions do not have an intent
trait BoxWriteIntent

//Writes caused by user actions
sealed trait BoxWriteUser extends BoxWriteIntent
object BoxWriteUserBrowse extends BoxWriteUser    //User is just browsing - the data model is not affected
object BoxWriteUserEdit extends BoxWriteUser      //User is editing the data model

object Box {

  val maximumReactionApplicationsPerCycle = 10000
  
  //Currently active reaction - if there is one, it is responsible
  //for reads/writes, if there is none then reads/writes are external
  private var activeReaction:Option[Reaction] = None

  //Currently active write intent, for the first write made in a cycle.
  //If none was registered, this is None
  //Can be used for example to distinguish user edits from changes by reactions
  //Only meaningful when accessed from reactions/views as part of react/respond
  private var _writeIntent: Option[BoxWriteIntent] = None
  
  //The box and changes for the first write made in a cycle.
  //Only meaningful when accessed from reactions/views as part of react/respond
  private var _firstWrite: Option[(Box[_, _], immutable.Queue[(Long, Change[_])])] = None
  
  //TODO should probably use a mutable.Queue, but they have a memory leak with last0
  //https://issues.scala-lang.org/browse/SI-6452
  //Reactions that WILL be processed this cycle
  private val reactionsPending = mutable.ArrayBuffer[Reaction]()

  //TODO Should be queue, as above
  //Reactions that are also views
  private val viewReactionsPending = mutable.ArrayBuffer[Reaction]()

  //During a cycle, maps each written box to a list of changes applied to that list in the current cycle
  //Actually the values are a pair of change index and change
  //Note that we don't bother with type for the values -
  //they are Queues of (Long, C) where C matches the Box[C] used as a key
  private val boxToChanges = new mutable.HashMap[Box[_,_], Object]

  //Threads that have asked not to be allowed to read
  private val nonReadingThreads = new mutable.HashSet[Thread]

  //For each reaction that has had any source change, maps to the set of boxes that have changed for that reaction. Allows
  //reactions to see why they have been called in any given cycle. Empty outside cycles. Note that from one call to
  //a reaction to the next, may acquire additional entries, etc.
  private val changedSourcesForReaction = new mutable.HashMap[Reaction, mutable.Set[Box[_,_]]] with MultiMap[Reaction, Box[_,_]]

  //The next change index to assign - start from 1 so that 0 is initial state of Boxes on creation.
  private var _changeIndex = 1L

  private var canRead = true
  private var canWrite = true
  private var checkingConflicts = false

  private var cycling = false

  private val lock = new ReentrantLock()

  private var decoding = false

  private var _cycleIndex = -1

  def cycleIndex = _cycleIndex

//  def changedSources(r:Reaction) = immutable.Set(changedSourcesForReaction.get(r).getOrElse(Set[Box[_,_]]()).toList:_*)
  def changedSources(r:Reaction) = immutable.Set[Box[_,_]](changedSourcesForReaction.get(r).getOrElse(Set[Box[_,_]]()).toList:_*)

  def decode[T](decode : =>T):T = {
    beforeDecode
    try {
      return decode
    } finally {
      afterDecode
    }
  }

  def withoutReading[T](action: =>T): T = {
    lock.lock()
    nonReadingThreads.add(Thread.currentThread())
    lock.unlock()
    try {
      return action
    } finally {
      lock.lock()
      nonReadingThreads.remove(Thread.currentThread())
      lock.unlock()
    }
  }

  def transact[T](transaction: =>T, intent: Option[BoxWriteIntent] = None): T = {
    lock.lock
    try {
      _writeIntent = intent
      return transaction
    } finally {
      _writeIntent = None
      lock.unlock
    }
  }
  
  def writeIntent = _writeIntent
  
  def firstWrite = _firstWrite

  def read[T](b: Box[_,_])(readOp: => T): T = {
    try {
      Box.beforeRead(b)
      readOp
    } finally {
      Box.afterRead(b)
    }    
  }
  
  private def beforeDecode() ={
    lock.lock
    decoding = true
  }

  private def afterDecode() = {
    if (!decoding) throw new RuntimeException("afterDecode called when not decoding")
    decoding = false

    //Any changes that have been made during deserialisation are considered
    //to be irrelevant, since serialised state is known to be valid. We will
    //still cycle though, so that all reactions can be treated as new, and
    //so will be run to establish sources and targets, etc.
    boxToChanges.clear
    cycle
    lock.unlock
  }

  def beforeRead(b:Box[_,_]) = {
    lock.lock
    if (!canRead) throw new InvalidReadException(b)

    //We allow reading when we are servicing reactions, since
    //these have their own enforcement of the timing of reading,
    //done using canRead above.
    if (activeReaction == None) {
      if (nonReadingThreads.contains(Thread.currentThread())) {
        throw new InvalidReadException(b)
      }
    }
  }

  def afterRead(b:Box[_,_]) = {
    //This box is a source of any active reaction
    activeReaction.foreach(r => associateReactionSource(r, b))
    lock.unlock
  }

  /**
   * Boxes must call before possibly writing. Returns whether
   * there is a reaction being applied, which can be used by Boxes
   * to reject writes. E.g. Cal will only accept writes from Reactions.
   */
  def beforeWrite(b:Box[_,_]) {
    lock.lock
    if (!canWrite) throw new InvalidWriteException(b)

    //This box is a target of any active reaction
    activeReaction.foreach(r => associateReactionTarget(r, b))
  }

  /**
   * Call after an actual write has been carried out on a box,
   * with a list of one or more changes that have been made, in
   * order. Note that there MUST be at least one change, to allow
   * for reactions to find the order of changes, etc.
   */
  def commitWrite[T, C <: Change[T]](b:Box[T,C], changes:C*) = {

    if (checkingConflicts) {
      activeReaction match {
        case Some(r) => {
          println("Conflicting changes: " + changes)
          throw new InvalidReactionException("Conflicting reaction", r, b)
        }
        case None => {
          throw new RuntimeException("Conflicting reaction with no active reaction - code error")
        }
      }
    }

    //Build a queue of the new changes, with change indices
    var newQ = immutable.Queue.empty[(Long, C)]
    changes.foreach(newChange => {
      newQ = newQ:+(_changeIndex, newChange)
      b._lastChangeIndex = _changeIndex
      _changeIndex += 1
    })

    
    
    if (activeReaction == None) {
      _firstWrite = Some(b, newQ)
    }
    
    var q = boxToChanges.get(b).getOrElse(immutable.Queue[(Long,C)]()).asInstanceOf[immutable.Queue[(Long,C)]] ++ newQ
    boxToChanges.put(b, q)

    //Any reactions on this box are now pending
    for {
      reaction <- b.sourcingReactions
    } pendReaction(reaction, List(b))

    cycle
  }

  def afterWrite(b:Box[_,_]) = {
    lock.unlock
  }

  private def associateReactionSource(r:Reaction, b:Box[_,_]) {
    r.sources.add(b)
    b.sourcingReactions.add(r)
  }

  private def associateReactionTarget(r:Reaction, b:Box[_,_]) {
    r.targets.add(b)
    b.targetingReactions.add(r)
  }

  private def pendReaction(r:Reaction, sourceBoxes:List[Box[_,_]] = List()) = {
    sourceBoxes.foreach(b => changedSourcesForReaction.addBinding(r, b))
    r.isView match {
      case false => {
        if (!reactionsPending.contains(r)) {
          reactionsPending.append(r)
        }
      }
      case true => {
        if (!viewReactionsPending.contains(r)) {
          viewReactionsPending.append(r)
        }
      }
    }
  }

  def boxFirstChangeIndex[T, C <: Change[T]](b:Box[T, C]) : Option[Long] = {
    //Reading a box's write index counts as reading it, and
    //so for example makes a reaction have the box as a source
    try {
      beforeRead(b)
      boxToChanges.get(b) match {
        case None => return None
        case Some(o) => return Some(o.asInstanceOf[immutable.Queue[(Long,C)]].head._1)
      }
    } finally {
      afterRead(b)
    }
  }

  def boxLastChangeIndex[T, C <: Change[T]](b:Box[T, C]) : Long = {
    //Reading a box's write index counts as reading it, and
    //so for example makes a reaction have the box as a source
    try {
      beforeRead(b)
      b._lastChangeIndex
    } finally {
      afterRead(b)
    }
  }
  
  def boxChanges[T, C <: Change[T]](b:Box[T, C]):Option[immutable.Queue[(Long,C)]] = {
    //Reading a box's changes counts as reading it, and
    //so for example makes a reaction have the box as a source
    try {
      beforeRead(b)
      return boxToChanges.get(b) match {
        case None => None
        //Note this is safe because we only ever accept changes
        //from a Box[C] of type C, and add them to list
        case Some(o) => Some(o.asInstanceOf[immutable.Queue[(Long,C)]])
      }
    } finally {
      afterRead(b)
    }
  }

  /**
   * Register a reaction - MUST be called after a reaction
   * is created, to allow it to trigger. Reaction will
   * trigger after this is called - either immediately
   * if we are NOT in a cycle, or towards the end of the cycle
   * if we are
   */
  def registerReaction(r:Reaction) = {
    //Requires lock
    lock.lock
    try {
      pendReaction(r)
      cycle
    } finally {
      lock.unlock
    }
  }

  private def cycle = {

    //Only enter one cycle at a time, and we don't cycle while decoding
    if (!cycling && !decoding) {
      cycling = true

      _cycleIndex += 1

      val failedReactions = new mutable.HashSet[Reaction]()

      val conflictReactions = new mutable.HashSet[Reaction]()
      
      val reactionApplications = new mutable.HashMap[Reaction, Int]().withDefaultValue(0)

      def removeReaction(r: Reaction) {
        clearReactionSourcesAndTargets(r)
        conflictReactions.remove(r)
        failedReactions.add(r)
        val filtered = reactionsPending.filter(_ != r)
        reactionsPending.clear()
        reactionsPending ++= filtered
      }
      
      //Keep cycling until we clear all reactions
      while (!reactionsPending.isEmpty || !viewReactionsPending.isEmpty) {

        val nextReaction = if (!reactionsPending.isEmpty) reactionsPending.remove(0) else viewReactionsPending.remove(0);

        //TODO at this point we should consider delaying
        //execution of reactions that have sources which
        //are also targets of pending reactions, to avoid
        //having to re-apply them later if those sources
        //are written as expected. This has the disadvantage
        //of not propagating reactions strictly "outwards"
        //from the first change, but this propagation order
        //is only really intended to assist in achieving the
        //expected result (e.g. for small cycles), not be a
        //firm guarantee, since in any case the propagation
        //order may not be completely predictable.

        //Clear this targets expected targets and sources,
        //so that they can be added from fresh by calling
        //reaction.respond and then applying that response
        //TODO should use temp set for tracking new sources, then
        //modify the sourceReactions from this, to allow for keeping the same
        //weak references (if appropriate) rather than regenerating every cycle.
        clearReactionSourcesAndTargets(nextReaction)

        try {

          reactionRespondAndApply(nextReaction)
          val applications = reactionApplications.getOrElse(nextReaction, 0)
          if (applications + 1 > maximumReactionApplicationsPerCycle) {
            throw new ReactionAppliedTooManyTimesInCycle(nextReaction, applications + 1, maximumReactionApplicationsPerCycle)
          } else {
            reactionApplications.put(nextReaction, applications + 1)
          }

          //We now have the correct targets for this reaction, so
          //we can track them for conflicts.
          //Any other reactions that target the
          //same box are potentially conflicting.
          for {
            target <- nextReaction.targets
            conflictReaction <- target.targetingReactions if (conflictReaction != nextReaction)
          } conflictReactions.add(conflictReaction)

        } catch {
          case e:BoxException => {
            println("Reaction failed with: " + e)
            //Remove the reaction completely from the system, but remember that it failed
            removeReaction(nextReaction)
          }
          case e:Exception => {
            println("Reaction failed with: " + e)
            //TODO need to respond better, but can't allow uncaught exception to just stop cycling
            e.printStackTrace()
            //Remove the reaction completely from the system, but remember that it failed
            removeReaction(nextReaction)
          }
        }

      }

      //Check all reactions whose TARGETS were changed
      //by other reactions are still happy with the state of their targets,
      //if they are not, this indicates a conflict and should generate a warning.
      //Note this is NOT the same as when a reaction is applied then has a source
      //changed, this should just result in the reaction being reapplied without
      //the expectation of no writes to its targets.
      //TODO should we have a specific way of checking this, by asking reactions
      //whether they are valid? NOTE we can't just ask them to return something special
      //from respond if they will do nothing, since this introduces a read of their source
      //which is bad to have - adds lots of false sources on reactions that may well
      //only want to apply in one direction. The current system is fine as long as boxes
      //all check for and ignore writes that make no difference, OR reactions return
      //responses that do nothing if they are valid. Actually this is probably best.
      checkingConflicts = true
      conflictReactions.foreach{
        r => {
          try {
            reactionRespondAndApply(r)
          } catch {
            case e:BoxException => {
              println("Reaction conflicted with box exception: " + e)
              e.printStackTrace()
              //Remove the reaction completely from the system, but remember that it failed
              clearReactionSourcesAndTargets(r)
              failedReactions.add(r)
            }
            case e:Exception => {
              println("Reaction conflicted with exception: " + e)
              //TODO need to respond better, but can't allow uncaught exception to just stop cycling
              e.printStackTrace()
              //Remove the reaction completely from the system, but remember that it failed
              clearReactionSourcesAndTargets(r)
              failedReactions.add(r)
            }
          }
        }
      }
      checkingConflicts = false

      //Only valid during cycling
      boxToChanges.clear()
      changedSourcesForReaction.clear()
      _firstWrite = None

      //Done for this cycle
      cycling = false

      if (!failedReactions.isEmpty) {
        println("Failed Reactions: " + failedReactions)
        //TODO make immutable copy of failed reactions for exception
        throw new FailedReactionsException()//Set(failedReactions))
      }
    }
  }

  def clearReactionSourcesAndTargets(r:Reaction) = {
    for {
      target <- r.targets
    } target.targetingReactions.remove(r)
    r.targets.clear
    for {
      source <- r.sources
    } source.sourcingReactions.remove(r)
    r.sources.clear
  }

  def reactionRespondAndApply(r:Reaction) = {

    activeReaction = Some(r)
    try {

//      canWrite = false
//      val response = r.respond
//      canWrite = true
//
//      if (!r.isView) {
//        canRead = false
//        response.apply
//      }
     
      canWrite = !r.isView
      //r.respond.apply()
      r.react()
      
    } finally {
      activeReaction = None
      canRead = true
      canWrite = true
    }

  }

}

/**
 * All Box changes give at least the old and new values of the Box
 */
trait Change[+T] {
  def oldValue: T
  def newValue: T
}

/**
 * One part of the boxes system. The other part is Reaction.
 *
 * A Box holds a single value, which may change over time, and
 * allows for reactions to those changes.
 * 
 * A Box also has a change type C, giving the type of data given
 * on each change.
 *
 * Be VERY careful making Boxes equal each other when they are not the SAME
 * Box (identical). This is because maps and sets are used for storing Boxes,
 * for example which Boxes are affected by a Reaction, and so equal Boxes will
 * be treated as the same Box - for example, only one of an equal set of Boxes
 * will be updated by a Reaction that might be intended to update more than one of them.
 *
 * However it is unlikely that you will need to implement a new Box in any case.
 */
trait Box[+T, +C <: Change[T]] {

  private[boxes] val sourcingReactions = new WeakHashSet[Reaction]()
  
  //FIXME this used to be non-weak, and probably can be without problems, but need to work out what is best
  private[boxes] val targetingReactions = new WeakHashSet[Reaction]()

  private[boxes] val retainedReactions = mutable.Set[Reaction]()

  def changes = Box.boxChanges(this)
  def firstChangeIndex = Box.boxFirstChangeIndex(this)

  def retainReaction(r:Reaction) = retainedReactions.add(r)
  def releaseReaction(r:Reaction) = retainedReactions.remove(r)
  
  private[boxes] var _lastChangeIndex = 0L

  def lastChangeIndex = Box.boxLastChangeIndex(this)

  def react (r:Reaction) = {
    Box.registerReaction(r)
    retainReaction(r)
    r
  }

  def react (r: =>Unit) = Reaction(this, r)
  
  def apply():T
  
}
