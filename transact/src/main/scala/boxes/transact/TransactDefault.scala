package boxes.transact

import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.concurrent.atomic.AtomicInteger
import scala.collection._
import java.lang.ref.WeakReference
import java.lang.ref.ReferenceQueue
import java.lang.ref.Reference
import scala.collection.mutable.ListBuffer
import scala.Option.option2Iterable


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

private class ShelfDefault extends Shelf {
  private val lock = RWLock()

  private var current = new RevisionDefault(0, Map.empty)
  
  private val refQueue = new ReferenceQueue[Box[_]]()
  private val refToId = new mutable.HashMap[Reference[_ <: Box[_]], Long]()

  private val retries = 10000
  
  //TODO can do this more efficiently without a full transaction
  def create[T](v: T): Box[T] = {
    transact{
      implicit t: Txn => {
        Box(v)
      }
    }
  }
  
  def read[T](f: TxnR => T): T = f(new TxnRDefault(now))
  
  def transact[T](f: Txn => T): T = {
    def tf(r: Revision) = new TxnSingle(r)
    transactRepeatedTry(f, tf, retries)
  }

  def transactMulti[T](f: Txn => T): T = {
    def tf(r: Revision) = new TxnMulti(r)
    transactRepeatedTry(f, tf, retries)
  }

  private def transactRepeatedTry[T](f: Txn => T, tf: Revision => TxnDefault, retries: Int): T = {
    Range(0, retries).view.map(_ => transactTry(f, tf)).find(o => o.isDefined).flatten.getOrElse(throw new RuntimeException("Transaction failed too many times"))
  }
  
  private def transactTry[T](f: Txn => T, transFactory: Revision => TxnDefault): Option[T] = {
    val t = transFactory(now)
    val r = f(t)
    
    lock.write {
      val start = t.revision.index
      //If we any boxes that were read have changed, we fail
      if (t.reads.iterator.flatMap(current.indexOfId(_)).exists(_>start)) {
//        println("read fail")
        None
      //Same for writes - note that newly created boxes will have no index, so will not fail
      } else if (t.writes.keysIterator.flatMap(current.indexOfId(_)).exists(_>start)) {
//        println("write fail")
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
//        println("success")
        Some(r)
      }
    }
  }
  
  def now = lock.read {
    current
  }
}

object ShelfDefault {
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

private class TxnMulti(revision: Revision) extends TxnSingle(revision) {
  val lock = RWLock()
  override def create[T](t: T): Box[T] = lock.write{super.create(t)}
  override def set[T](box: Box[T], t: T): Box[T] = lock.write{super.set(box, t)}
  override def get[T](box: Box[T]): T = lock.read{super.get(box)}
}



