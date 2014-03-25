package boxes.transact

import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.concurrent.atomic.AtomicInteger
import scala.collection._
import java.lang.ref.WeakReference
import java.lang.ref.ReferenceQueue
import java.lang.ref.Reference
import scala.collection.mutable.ListBuffer
import scala.Option.option2Iterable

class RWLock() {
  private val lock: ReentrantReadWriteLock = new ReentrantReadWriteLock()
  
  def write[T](w: =>T): T = {
    lock.writeLock().lock()
    try {
      return w
    } finally {
      lock.writeLock().unlock()
    }
  }

  def read[T](r: =>T): T = {
    lock.readLock().lock()
    try {
      return r
    } finally {
      lock.readLock().unlock()
    }
  }
}

object RWLock {
  def apply() = new RWLock()
}

trait Box[T] {
  def apply()(implicit t: TransactionTry): T = t.get(this)
  def get()(implicit t: TransactionTry): T = apply()(t)
  
  def update(v: T)(implicit t: TransactionTry) = t.set(this, v)
  def set(v: T)(implicit t: TransactionTry) = update(v)(t)
  
  def id(): Long
}

object Box {
  def apply[T](v: T)(implicit t: TransactionTry): Box[T] = t.create(v)
}

private class BoxDefault[T](val id: Long) extends Box[T]

private object BoxDefault {
  private val nextId = new AtomicInteger(0)
  def apply[T](): Box[T] = new BoxDefault[T](nextId.getAndIncrement())
}

case class State[T](revision: Long, value: T)

trait Revision {
  val index: Long
  
  def stateOf[T](box: Box[T]): Option[State[T]]
  def indexOf(box: Box[_]): Option[Long]
  def valueOf[T](box: Box[T]): Option[T]
  
  def apply[T](box: Box[T]) = stateOf[T](box)
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

trait Shelf {
  def now: Revision
  def transact[T](f: TransactionTry => T): T
  def create[T](t: T): Box[T]
}

private class ShelfDefault extends Shelf {
  private val lock = RWLock()

  private var current = new RevisionDefault(0, Map.empty)
  
  private val refQueue = new ReferenceQueue[Box[_]]()
  private val refToId = new mutable.HashMap[Reference[_ <: Box[_]], Long]()

//  def apply[T](box: Box[T])(implicit r: Revision) = r.values.get(box).getOrElse(throw new RuntimeException("Box does not exist in current revision")).data
  
  //TODO can do this more efficiently without a full transaction
  def create[T](v: T): Box[T] = {
    transact{
      implicit t: TransactionTry => {
        Box(v)
      }
    }
  }
  
  def transact[T](f: TransactionTry => T): T = {
      Range(0, 10000).view.map(_ => transactTry(f)).find(o => o.isDefined).flatten.getOrElse(throw new RuntimeException("Transaction failed too many times"))
  }
  
  private def transactTry[T](f: TransactionTry => T): Option[T] = {
    val t = new TransactionTryDefault(now)
    val r = f(t)
    
    lock.write {
      val start = t.revision.index
      //If we any boxes that were read have changed, we fail
      if (t.reads.iterator.flatMap(current.indexOfId(_)).exists(_>start)) {
        None
      //Same for writes - note that newly created boxes will have no index, so will not fail
      } else if (t.writes.keysIterator.flatMap(current.indexOfId(_)).exists(_>start)) {
        None
      } else {
        
        //Any new boxes need to be tracked for GC - make a weak reference to the box, and use that to map to the id of the box
        t.creates.filter(box => current.stateOf(box).isEmpty).foreach{b => {
            val r = new WeakReference(b, refQueue)
            refToId.put(r, b.id)
          }
        }
        
        //Now we also check for any boxes that have been GCed since last update
        val gcedIds = new ListBuffer[Long]()
        var gced = refQueue.poll()
        while (gced != null) {
          val id = refToId.remove(gced)
          id.foreach(gcedIds += _)
          gced = refQueue.poll()
        }
        
        val deletes = gcedIds.toList
        val next = current.updated(t.writes, deletes)
        current = next
        Some(r)
      }
    }
  }
  
  def now = {
    lock.read {
      current
    }
  } 
}

object Shelf {
  def apply(): Shelf = new ShelfDefault
}

trait TransactionTry {
  def create[T](t: T): Box[T]
  def set[T](box: Box[T], t: T): Box[T]
  def get[T](box: Box[T]): T
  def revision(): Revision
}

private class TransactionTryDefault(val revision: Revision) extends TransactionTry {
  //TODO if the transaction code is guaranteed to be single-threaded, we don't need the lock
  val lock = RWLock()
  val writes = new mutable.HashMap[Long, Any]()
  val reads = new mutable.HashSet[Long]()
  val creates = new mutable.HashSet[Box[_]]()
  
  def create[T](t: T): Box[T] = lock.write{
    val box = BoxDefault[T]()
    creates.add(box)
    writes.put(box.id, t)
    box
  }
  
  def set[T](box: Box[T], t: T): Box[T] = lock.write{
    writes.put(box.id, t)
    box
  }
  def get[T](box: Box[T]): T = lock.read{
    val v = writes.get(box.id).asInstanceOf[Option[T]].getOrElse(revision.valueOf(box).getOrElse(throw new RuntimeException("Missing Box")))
    reads.add(box.id)
    return v
  }
}



