package boxes.transact

import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.concurrent.atomic.AtomicInteger
import scala.collection._
import java.lang.ref.WeakReference
import java.lang.ref.ReferenceQueue
import java.lang.ref.Reference
import scala.collection.mutable.ListBuffer
import scala.Option.option2Iterable
import java.util.concurrent.ExecutorService
import boxes.transact.util.RWLock

trait Identifiable {
  def id(): Long
}

trait Box[T] extends Identifiable {
  def apply()(implicit t: TxnR): T = t.get(this)
  def get()(implicit t: TxnR): T = apply()(t)
  
  def update(v: T)(implicit t: Txn) = t.set(this, v)
  def set(v: T)(implicit t: Txn) = update(v)(t)
}

object Box {
  def apply[T](v: T)(implicit t: Txn): Box[T] = t.create(v)
}

case class State[T](revision: Long, value: T)

trait Revision {
  val index: Long
  
  def stateOf[T](box: Box[T]): Option[State[T]]
  def indexOf(box: Box[_]): Option[Long]
  def valueOf[T](box: Box[T]): Option[T]
  
  def apply[T](box: Box[T]) = stateOf[T](box)
}

trait Shelf {
  def now: Revision
  
  def create[T](t: T): Box[T]

  def transact[T](f: Txn => T): T
  def read[T](f: TxnR => T): T
  

  def view(f: TxnR => Unit): Long
  def view(f: TxnR => Unit, exe: ExecutorService, onlyMostRecent: Boolean): Long
  
  def auto[T](f: Txn => T): Long  
  def auto[T](f: Txn => T, exe: ExecutorService, target: T => Unit): Long
}

trait TxnR {
  def get[T](box: Box[T]): T
  def revision(): Revision  
}

trait Txn extends TxnR {
  def create[T](t: T): Box[T]
  def set[T](box: Box[T], t: T): Box[T]
  
  def react(f: Txn => Boolean)
  
  def failEarly(): Unit
}

trait View

trait Auto

class TxnEarlyFailException(msg: String = "") extends Exception

private class TxnMulti(txn: Txn) extends Txn{
  val lock = RWLock()
  override def create[T](t: T): Box[T] = lock.write{txn.create(t)}
  override def set[T](box: Box[T], t: T): Box[T] = lock.write{txn.set(box, t)}
  override def react(f: Txn => Boolean) = lock.write{txn.react(f)}

  override def get[T](box: Box[T]): T = lock.read{txn.get(box)}
  override def revision() = txn.revision()
  override def failEarly() = txn.failEarly()
}

/** This can be used to wrap a Txn to make it safe for concurrent multi-threaded
  * access, by using a RWLock(). 
  */
object TxnMulti {
  def apply(txn: Txn): Txn = new TxnMulti(txn)
}

