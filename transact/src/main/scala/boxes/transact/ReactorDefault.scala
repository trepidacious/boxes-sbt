package boxes.transact

private class ReactorDefault(txn: TxnForReactor, val maximumReactionApplicationsPerCycle: Int = 10000) extends ReactorForTxn with ReactorTxn {
  
  //For each reaction that has had any source change, maps to the set of boxes that have changed for that reaction. Allows
  //reactions to see why they have been called in any given cycle. Empty outside cycles. Note that from one call to
  //a reaction to the next, may acquire additional entries, etc.
  private val changedSourcesForReaction = new scala.collection.mutable.HashMap[Long, scala.collection.mutable.Set[Box[_]]] with scala.collection.mutable.MultiMap[Long, Box[_]]

  //TODO should probably use a mutable.Queue, but they have a memory leak with last0
  //https://issues.scala-lang.org/browse/SI-6452
  //Ids of reactions that WILL be processed this cycle
  private val reactionsPending = scala.collection.mutable.ArrayBuffer[Long]()

  private var cycling = false
  private var decoding = false
  private var checkingConflicts = false

  //Currently active reaction id - if there is one, it is responsible
  //for reads/writes, if there is none then reads/writes are external
  private var activeReaction:Option[Long] = None

  def changedSources = changedSourcesForReaction.get(activeReaction.getOrElse(throw new RuntimeException("No active reaction in call to changedSources - code error"))).getOrElse(Set.empty).toSet
  
  def afterSet[T](box: Box[T], t: T) {
    //This box is a target of any active reaction
    activeReaction.foreach(r => txn.addTargetForReaction(r, box.id))
    
    if (checkingConflicts) {
      activeReaction match {
        case Some(r) => {
          throw new ConflictingReactionException("Conflicting reaction")
        }
        case None => {
          throw new RuntimeException("Conflicting reaction with no active reaction - code error")
        }
      }
    }

    //Any reactions on this box are now pending
    for {
      reaction <- txn.reactionsSourcingBox(box.id)
    } pendReaction(reaction, List(box))

    cycle
  }
  
  def afterGet[T](box: Box[T]) {
    //This box is a source of any active reaction
    activeReaction.foreach(txn.addSourceForReaction(_, box.id))
  }
  
  def registerReaction(r: ReactionDefault) {
    pendReaction(r.id)
    cycle
  }
  
  private def pendReaction(rid: Long, sourceBoxes:List[Box[_]] = List()) = {
    sourceBoxes.foreach(b => changedSourcesForReaction.addBinding(rid, b))
    if (!reactionsPending.contains(rid)) {
      reactionsPending.append(rid)
    }
  }
  
  private def cycle = {
    //Only enter one cycle at a time, and we don't cycle while decoding
    if (!cycling && !decoding) {
      cycling = true
      performCycle()
    }
  }
  
  private def performCycle() = {
    
    try {
      val failedReactions = new scala.collection.mutable.HashSet[Long]()
  
      //Ids of reactions that may be in conflict with other reactions
      val conflictReactions = new scala.collection.mutable.HashSet[Long]()

      //Number of times each reaction has run in this cycle
      val reactionApplications = new scala.collection.mutable.HashMap[Long, Int]().withDefaultValue(0)

      //Keep cycling until we clear all reactions
      while (!reactionsPending.isEmpty) {
  
        val nextReaction = reactionsPending.remove(0)
  
        //Clear this targets expected targets and sources,
        //so that they can be added from fresh by calling
        //reaction.respond and then applying that response
        txn.clearReactionSourcesAndTargets(nextReaction)
  
        try {
  
          reactionRespondAndApply(nextReaction)
  
          val applications = reactionApplications.getOrElse(nextReaction, 0)
          if (applications + 1 > maximumReactionApplicationsPerCycle) {
            throw new ReactionAppliedTooManyTimesInCycle()
          } else {
            reactionApplications.put(nextReaction, applications + 1)
          }

          //We now have the correct targets for this reaction, so
          //we can track them for conflicts
          for {
            target <- txn.targetsOfReaction(nextReaction)
            conflictReaction <- txn.reactionsTargettingBox(target) if (conflictReaction != nextReaction)
          } conflictReactions.add(conflictReaction)
  
        } catch {
          //TODO If this is NOT a BoxException, need to respond better, but can't allow uncaught exception to just stop cycling
          case e:Exception => {
            println("Reaction failed with: " + e)
            e.printStackTrace()
            //Remove the reaction completely from the system, but remember that it failed
            txn.clearReactionSourcesAndTargets(nextReaction)
            conflictReactions.remove(nextReaction)
            failedReactions.add(nextReaction)
            val filtered = reactionsPending.filter(_ != nextReaction)
            reactionsPending.clear()
            reactionsPending ++= filtered
          }
        }
  
      }
  
      //Check all reactions whose TARGETS were changed
      //by other reactions are still happy with the state of their targets,
      //if they are not, this indicates a conflict and should generate a warning.
      //Note this is NOT the same as when a reaction is applied then has a source
      //changed, this should just result in the reaction being reapplied without
      //the expectation of no writes to its targets.
      checkingConflicts = true
      conflictReactions.foreach{
        r => {
          try {
            reactionRespondAndApply(r)
          } catch {
            //TODO if this is NOT a BoxException, need to respond better, but can't allow uncaught exception to just stop cycling
            case e:Exception => {
              println("Reaction conflicted with exception: " + e)
              e.printStackTrace()
              //Remove the reaction completely from the system, but remember that it failed
              txn.clearReactionSourcesAndTargets(r)
              failedReactions.add(r)
            }
          }
        }
      }
      checkingConflicts = false
  
      //Only valid during cycling
      changedSourcesForReaction.clear()
  
      //Done for this cycle
      cycling = false
  
      if (!failedReactions.isEmpty) {
        println("Failed Reactions: " + failedReactions)
        //TODO make immutable copy of failed reactions for exception
        throw new FailedReactionsException()//Set(failedReactions))
      }
    } finally {
      txn.reactionFinished()
    }
  }
  
  def reactionRespondAndApply(rid: Long) = {
    activeReaction = Some(rid)
    try {
      txn.react(rid)
    } finally {
      activeReaction = None
    }
  }
  
  def create[T](t: T) = txn.create(t) 
  def createReaction(f: boxes.transact.ReactorTxn => Unit) = txn.createReaction(f)
  def failEarly() = txn.failEarly()
  def set[T](box: boxes.transact.Box[T], t: T) = txn.set(box, t)
  def get[T](box: boxes.transact.Box[T]) = txn.get(box)
  def revision() = txn.revision
  def boxReleasesReaction(box: boxes.transact.Box[_],r: boxes.transact.Reaction) = txn.boxReleasesReaction(box, r)
  def boxRetainsReaction(box: boxes.transact.Box[_],r: boxes.transact.Reaction) = txn.boxRetainsReaction(box, r)
  
}