package boxes.transact

import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.concurrent.atomic.AtomicInteger
import scala.collection._
import java.lang.ref.WeakReference
import java.lang.ref.ReferenceQueue
import java.lang.ref.Reference
import scala.collection.mutable.ListBuffer
import scala.Option.option2Iterable
import scala.collection.mutable.ConcurrentMap
import java.util.concurrent.ExecutorService
import boxes.util.WeakHashSet
import java.util.concurrent.Executors
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import boxes.transact.util.RWLock
import boxes.transact.util.GCWatcher
import boxes.transact.util.DaemonThreadFactory
import boxes.transact.util.Lock
import scala.collection.immutable.HashSet
import boxes.transact.util.BiMultiMap
import scala.collection.mutable.MultiMap


private class BoxDefault[T](val id: Long) extends Box[T]

private object BoxDefault {
  private val nextId = new AtomicInteger(0)
  def apply[T](): Box[T] = new BoxDefault[T](nextId.getAndIncrement())
}

private class ReactionDefault(val id: Long) extends Reaction

private object ReactionDefault {
  private val nextId = new AtomicInteger(0)
  def apply() = new ReactionDefault(nextId.getAndIncrement())  
}

private class RevisionDefault(val index: Long, val map: Map[Long, State[_]], reactionMap: Map[Long, Txn=>Unit], val sources: BiMultiMap[Long, Long], val targets: BiMultiMap[Long, Long]) extends Revision {

  def stateOf[T](box: Box[T]): Option[State[T]] = map.get(box.id).asInstanceOf[Option[State[T]]]
  def indexOf(box: Box[_]): Option[Long] = map.get(box.id).map(_.revision)
  def valueOf[T](box: Box[T]): Option[T] = stateOf(box).map(_.value)

  def indexOfId(id: Long): Option[Long] = map.get(id).map(_.revision)

  def reactionOfId(id: Long): Option[Txn=>Unit] = reactionMap.get(id)
  
  def updated(writes: Map[Long, _], deletes: List[Long], newReactions: Map[Reaction, Txn=>Unit], reactionDeletes: List[Long], sources: BiMultiMap[Long, Long], targets: BiMultiMap[Long, Long]) = {
    val newIndex = index + 1
    val prunedMap = deletes.foldLeft(map){case (map, id) => map - id}
    val newMap = writes.foldLeft(prunedMap){case (map, (id, value)) => map.updated(id, State(newIndex, value))}
    
    val prunedReactionMap = reactionDeletes.foldLeft(reactionMap){case (map, id) => map - id}
    val newReactionMap = newReactions.foldLeft(prunedReactionMap){case (map, (reaction, f)) => map.updated(reaction.id, f)}
    
    new RevisionDefault(newIndex, newMap, newReactionMap, sources, targets)
  } 

  def conflictsWith(t: TxnDefault) = {
    val start = t.revision.index
    (t.reads.iterator.flatMap(indexOfId(_)).exists(_>start)) || 
    (t.writes.keysIterator.flatMap(indexOfId(_)).exists(_>start))
  }
}

private class ViewDefault(val shelf: ShelfDefault, val f: TxnR => Unit, val exe: ExecutorService, onlyMostRecent: Boolean = true) extends View {
  private val revisionQueue = new mutable.Queue[RevisionDefault]()
  private val lock = Lock()
  private var state: Option[(Long, Set[Long])] = None
  private var pending = false;

  private def relevant(r: RevisionDefault) = {
    state match {
      case None => true
      case Some((index, reads)) => reads.iterator.flatMap(r.indexOfId(_)).exists(_>index)
    }
  }
  
  private def go() {
    
    //If we have more revisions pending, try to run the next
    if (!revisionQueue.isEmpty) {
      val r = revisionQueue.dequeue
      
      //If this revision is relevant (i.e. it has changes the view will read)
      //then run the transaction on it
      if (relevant(r)) {
        pending = true
        val t = new TxnRLogging(r)

        exe.execute(new Runnable() {
          def run = {
            f(t)
            lock.run{
              state = Some((r.index, t.reads))
              go()
            }
          }
        })

      //If this revision is NOT relevant, try the next revision
      } else {
        go()
      }
      
    //If we have no more revisions, then stop for now
    } else {
      pending = false
    }
  }
  
  def add(r: RevisionDefault) {
    lock.run {
      if (onlyMostRecent) revisionQueue.clear
      revisionQueue.enqueue(r)
      if (!pending) {
        go()
      }
    }
  }
}

private class AutoDefault[T](val shelf: ShelfDefault, val f: Txn => T, val exe: ExecutorService, target: T => Unit = (t:T) => Unit) extends Auto {
  private val revisionQueue = new mutable.Queue[RevisionDefault]()
  private val lock = Lock()
  private var state: Option[(Long, Set[Long])] = None
  private var pending = false;

  private def relevant(r: RevisionDefault) = {
    state match {
      case None => true
      case Some((index, reads)) => reads.iterator.flatMap(r.indexOfId(_)).exists(_>index)
    }
  }
  
  private def go() {
    
    //If we have more revisions pending, try to run the next
    if (!revisionQueue.isEmpty) {
      val r = revisionQueue.dequeue
      
      //If this revision is relevant (i.e. it has changes the transaction will read)
      //then run the transaction on it
      if (relevant(r)) {
        pending = true
        exe.execute(new Runnable() {
          def run = {
            val (result, t) = shelf.transactFromAuto(f)
            lock.run{
              state = Some((r.index, t.reads))
              go()
            }
            target(result)
          }
        })

      //If this revision is NOT relevant, try the next revision
      } else {
        go()
      }
      
    //If we have no more revisions, then stop for now
    } else {
      pending = false
    }
  }
  
  def add(r: RevisionDefault) {
    lock.run {
      revisionQueue.clear
      revisionQueue.enqueue(r)
      if (!pending) {
        go()
      }
    }
  }
}

private class ShelfDefault extends Shelf {
  private val lock = RWLock()

  private var current = new RevisionDefault(0, Map.empty, Map.empty, BiMultiMap.empty, BiMultiMap.empty)

  private val retries = 10000
  
  private val watcher = new GCWatcher()
  private val reactionWatcher = new GCWatcher()
  
  private val views = new WeakHashSet[ViewDefault]()
  private val autos = new WeakHashSet[AutoDefault[_]]()
  
  //TODO can we do this more efficiently without a full transaction?
  def create[T](v: T): Box[T] = transact{
    implicit t: Txn => {
      Box(v)
    }
  }
  
  def read[T](f: TxnR => T): T = f(new TxnRDefault(this, now))

  def view(f: TxnR => Unit) = view(f, ShelfDefault.defaultExecutorService, true)
  
  def view(f: TxnR => Unit, exe: ExecutorService = ShelfDefault.defaultExecutorService, onlyMostRecent: Boolean = true): Long = {
    lock.write {
      val view = new ViewDefault(this, f, exe, onlyMostRecent)
      views.add(view)
      view.add(current)
      current.index
    }
  }

  def auto[T](f: Txn => T): Long = auto(f, ShelfDefault.defaultExecutorService, (t:T) => Unit)
  
  def auto[T](f: Txn => T, exe: ExecutorService = ShelfDefault.defaultExecutorService, target: T => Unit = (t: T) => Unit): Long = {
    lock.write {
      val auto = new AutoDefault(this, f, exe, target)
      autos.add(auto)
      auto.add(current)
      current.index
    }
  }
  
  def transactFromAuto[T](f: Txn => T): (T, TxnDefault) = {
    def tf(r: RevisionDefault) = new TxnDefault(this, r)
    transactRepeatedTry(f, tf, retries)
  }

  def transact[T](f: Txn => T): T = {
    def tf(r: RevisionDefault) = new TxnDefault(this, r)
    transactRepeatedTry(f, tf, retries)._1
  }

  def transactRepeatedTry[T, TT <: TxnDefault](f: Txn => T, tf: RevisionDefault => TT, retries: Int): (T, TT) = {
    Range(0, retries).view.map(_ => transactTry(f, tf)).find(o => o.isDefined).flatten.getOrElse(throw new RuntimeException("Transaction failed too many times"))
  }
  
  private def revise(updated: RevisionDefault) {
    current = updated
    
    //TODO this can be done outside the lock by just passing the new revision to a queue to be
    //consumed by another thread that actually updated views
    views.foreach(_.add(updated))
    autos.foreach(_.add(updated))
  }
  
  private def transactTry[T, TT <: TxnDefault](f: Txn => T, transFactory: RevisionDefault => TT): Option[(T, TT)] = {
    val t = transFactory(now)
    val tryR = Try(f(t))
    
    //TODO note we could just lock long enough to get the current revision, and build the new map
    //outside the lock, then re-lock to attempt to make the new map the next revision, failing if
    //someone else got there first. This would make the write lock VERY brief, but potentially require
    //multiple rebuilds of the map before one "sticks"
    lock.write {
      
      tryR match {
        case Success(r) if !current.conflictsWith(t) => {
          //Watch new boxes, make new revision with GCed boxes deleted, and return result and successful transaction
          watcher.watch(t.creates)
          reactionWatcher.watch(t.reactionCreates.keySet)
          revise(current.updated(t.writes, watcher.deletes(), t.reactionCreates, reactionWatcher.deletes(), t.sources, t.targets))
          Some((r, t))
        }
        case Failure(e: TxnEarlyFailException) => None  //Exception indicating early failure, e.g. due to conflict
        case Failure(e) => throw e                      //Exception that is not part of transaction system
        case _ => None                                  //Conflict
      }

    }
  }
  
  def now = lock.read {
    current
  }
  
  def react(f: Txn => Unit) = transact{
    implicit txn => {
      txn.createReaction{f}
    }
  }
  
}

object ShelfDefault {
  val defaultExecutorPoolSize = 8
  val defaultThreadFactory = DaemonThreadFactory()
  lazy val defaultExecutorService = Executors.newFixedThreadPool(defaultExecutorPoolSize, defaultThreadFactory)

  def apply(): Shelf = new ShelfDefault
}


private class TxnRDefault(val shelf: ShelfDefault, val revision: RevisionDefault) extends TxnR {
  def get[T](box: Box[T]): T = revision.valueOf(box).getOrElse(throw new RuntimeException("Missing Box"))
}

private class TxnRLogging(val revision: RevisionDefault) extends TxnR {
  val reads = new mutable.HashSet[Long]()
  def get[T](box: Box[T]): T = {
    val v = revision.valueOf(box).getOrElse(throw new RuntimeException("Missing Box"))
    reads.add(box.id)
    return v
  }
}

private class TxnDefault(val shelf: ShelfDefault, val revision: RevisionDefault) extends ReactorTxn {
  
  val writes = new mutable.HashMap[Long, Any]()
  val reads = new mutable.HashSet[Long]()
  val creates = new mutable.HashSet[Box[_]]()
  val reactionCreates = new mutable.HashMap[Reaction, Txn=>Unit]()
  val reactionIdCreates = new mutable.HashMap[Long, Txn=>Unit]()
  var sources = revision.sources
  var targets = revision.targets
  
  var currentReactor: Option[ReactorDefault] = None
  
  def create[T](t: T): Box[T] = {
    val box = BoxDefault[T]()
    creates.add(box)
    writes.put(box.id, t)
    box
  }
  
  def set[T](box: Box[T], t: T): Box[T] = {
    //If box value would not be changed, skip write
    if (_get(box) != t) {
      writes.put(box.id, t)
      withReactor(_.afterSet(box, t))
    }
    box
  }
  
  private def _get[T](box: Box[T]): T = writes.get(box.id).asInstanceOf[Option[T]].getOrElse(revision.valueOf(box).getOrElse(throw new RuntimeException("Missing Box")))
  
  def get[T](box: Box[T]): T = {
    val v = _get(box)
    reads.add(box.id)
//    currentReaction.foreach(r => sources = sources.updated(r.id, sources.valuesFor(r.id) + box.id))
    //Only need to use a reactor if one is active
    currentReactor.foreach(_.afterGet(box))
    return v
  }
  
  def createReaction(f: Txn => Unit): Reaction = {
    val reaction = ReactionDefault()
    reactionCreates.put(reaction, f)
    reactionIdCreates.put(reaction.id, f)
    withReactor(_.registerReaction(reaction))
    reaction
  }
  
  def withReactor[T](action: ReactorDefault => T): T = {
    currentReactor match {
      case Some(r) => {
        action(r)
      }
      case None => {
        val r = new ReactorDefault(this)
        currentReactor = Some(r)
        action(r)
      }
    } 
  }
  
  
  def failEarly() = if (shelf.now.conflictsWith(this)) throw new TxnEarlyFailException
  
  
  def reactionFinished() {
    currentReactor = None
    println("Reaction finished, sources " + sources + ", targets " + targets)
    println("Boxes " + revision.map)
  }
  
  def clearReactionSourcesAndTargets(rid: Long) {
    sources = sources.removedKey(rid)
    targets = targets.removedKey(rid)
  }
  
  def targetsOfReaction(rid: Long) = targets.valuesFor(rid)
  def sourcesOfReaction(rid: Long) = sources.valuesFor(rid)
  
  def reactionsTargettingBox(bid: Long) = targets.keysFor(bid)
  def reactionsSourcingBox(bid: Long) = sources.keysFor(bid)
  
  private def reactionFunctionForId(rid: Long): Txn => Unit = {
    reactionIdCreates.get(rid).getOrElse(revision.reactionOfId(rid).getOrElse(throw new RuntimeException("Missing Reaction")))
  }
  
  def react(rid: Long) = reactionFunctionForId(rid).apply(this)
  
  def addTargetForReaction(rid: Long, bid: Long) = {
    println("Reaction " + rid + " targets " + bid)
    targets = targets.updated(rid, targets.valuesFor(rid) + bid)
  }

  def addSourceForReaction(rid: Long, bid: Long) = {
    println("Reaction " + rid + " sources " + bid)
    sources = sources.updated(rid, sources.valuesFor(rid) + bid)
  }

}


