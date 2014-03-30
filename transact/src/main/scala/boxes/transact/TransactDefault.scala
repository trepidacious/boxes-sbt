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


private class BoxDefault[T](val id: Long) extends Box[T]

private object BoxDefault {
  private val nextId = new AtomicInteger(0)
  def apply[T](): Box[T] = new BoxDefault[T](nextId.getAndIncrement())
}

private class RevisionDefault(val index: Long, map: Map[Long, State[_]]) extends Revision {

  def stateOf[T](box: Box[T]): Option[State[T]] = map.get(box.id).asInstanceOf[Option[State[T]]]
  def indexOf(box: Box[_]): Option[Long] = map.get(box.id).map(_.revision)
  def valueOf[T](box: Box[T]): Option[T] = stateOf(box).map(_.value)

  def indexOfId(id: Long): Option[Long] = map.get(id).map(_.revision)

  def updated(writes: Map[Long, _], deletes: List[Long]) = {
    val newIndex = index + 1
    val prunedMap = deletes.foldLeft(map){case (map, id) => map - id}
    val newMap = writes.foldLeft(prunedMap){case (map, (id, value)) => map.updated(id, State(newIndex, value))}
    new RevisionDefault(newIndex, newMap)
  } 
}

private class ViewDefault(val f: TxnR => Unit, val exe: ExecutorService, onlyMostRecent: Boolean = true) extends View {
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

private class ShelfDefault extends Shelf {
  private val lock = RWLock()

  private var current = new RevisionDefault(0, Map.empty)

  private val retries = 10000
  
  private val watcher = new BoxGCWatcher()
  
  private val views = new WeakHashSet[ViewDefault]()
  
  //TODO can we do this more efficiently without a full transaction?
  def create[T](v: T): Box[T] = transact{
    implicit t: Txn => {
      Box(v)
    }
  }
  
  def read[T](f: TxnR => T): T = f(new TxnRDefault(now))

  def view(f: TxnR => Unit) = view(f, ShelfDefault.defaultExecutorService, true)
  
  def view(f: TxnR => Unit, exe: ExecutorService = ShelfDefault.defaultExecutorService, onlyMostRecent: Boolean = true): Long = {
    lock.write {
      val view = new ViewDefault(f, exe, onlyMostRecent)
      views.add(view)
      view.add(current)
      current.index
    }
  }

  def transact[T](f: Txn => T): T = {
    def tf(r: Revision) = new TxnSingle(r)
    transactRepeatedTry(f, tf, retries)
  }

//  def transactMulti[T](f: Txn => T): T = {
//    def tf(r: Revision) = new TxnMulti(r)
//    transactRepeatedTry(f, tf, retries)
//  }

  private def transactRepeatedTry[T](f: Txn => T, tf: Revision => TxnDefault, retries: Int): T = {
    Range(0, retries).view.map(_ => transactTry(f, tf)).find(o => o.isDefined).flatten.getOrElse(throw new RuntimeException("Transaction failed too many times"))
  }
  
  private def revise(updated: RevisionDefault) {
    current = updated
    
    //TODO this can be done outside the lock by just passing the new revision to a queue to be
    //consumed by another thread that actually updated views
    views.foreach(_.add(updated))
  }
  
  private def transactTry[T](f: Txn => T, transFactory: Revision => TxnDefault): Option[T] = {
    val t = transFactory(now)
    val r = f(t)
    
    //TODO note we could just lock long enough to get the current revision, and build the new map
    //outside the lock, then re-lock to attempt to make the new map the next revision, failing if
    //someone else got there first. This would make the write lock VERY brief, but potentially require
    //multiple rebuilds of the map before one "sticks"
    lock.write {
      val start = t.revision.index
      //If we any boxes that were read have changed, we fail
      if (t.reads.iterator.flatMap(current.indexOfId(_)).exists(_>start)) {
        None
      //Same for writes - note that newly created boxes will have no index, so will not fail
      } else if (t.writes.keysIterator.flatMap(current.indexOfId(_)).exists(_>start)) {
        None
      } else {
        
        //Any new boxes need to be tracked for GC
        watcher.watch(t.creates)
        
        //Update map with the writes, also deleting any boxes that have been GCed
        revise(current.updated(t.writes, watcher.deletes()))
        
        //Success, return Txn result
        Some(r)
      }
    }
  }
  
  def now = lock.read {
    current
  }
}

object ShelfDefault {
  val defaultExecutorPoolSize = 8
  val defaultThreadFactory = DaemonThreadFactory()
  lazy val defaultExecutorService = Executors.newFixedThreadPool(defaultExecutorPoolSize, defaultThreadFactory)

  def apply(): Shelf = new ShelfDefault
}

private trait TxnDefault extends Txn {
  def reads(): Set[Long]
  def writes(): Map[Long, Any]
  def creates(): Set[Box[_]]
}

private class TxnRDefault(val revision: Revision) extends TxnR {
  def get[T](box: Box[T]): T = revision.valueOf(box).getOrElse(throw new RuntimeException("Missing Box"))
}

private class TxnRLogging(val revision: Revision) extends TxnR {
  val reads = new mutable.HashSet[Long]()
  def get[T](box: Box[T]): T = {
    val v = revision.valueOf(box).getOrElse(throw new RuntimeException("Missing Box"))
    reads.add(box.id)
    return v
  }
}

private class TxnSingle(val revision: Revision) extends TxnDefault {
  
  val writes = new mutable.HashMap[Long, Any]()
  val reads = new mutable.HashSet[Long]()
  val creates = new mutable.HashSet[Box[_]]()
  
  def create[T](t: T): Box[T] = {
    val box = BoxDefault[T]()
    creates.add(box)
    writes.put(box.id, t)
    box
  }
  
  def set[T](box: Box[T], t: T): Box[T] = {
    writes.put(box.id, t)
    box
  }
  def get[T](box: Box[T]): T = {
    val v = writes.get(box.id).asInstanceOf[Option[T]].getOrElse(revision.valueOf(box).getOrElse(throw new RuntimeException("Missing Box")))
    reads.add(box.id)
    return v
  }
}

//private class TxnMulti(revision: Revision) extends TxnSingle(revision) {
//  val lock = RWLock()
//  override def create[T](t: T): Box[T] = lock.write{super.create(t)}
//  override def set[T](box: Box[T], t: T): Box[T] = lock.write{super.set(box, t)}
//  override def get[T](box: Box[T]): T = lock.read{super.get(box)}
//}



