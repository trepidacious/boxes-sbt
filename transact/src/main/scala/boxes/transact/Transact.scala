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

trait Box[T] {
  def apply()(implicit t: TxnR): T = t.get(this)
  def get()(implicit t: TxnR): T = apply()(t)
  
  def update(v: T)(implicit t: Txn) = t.set(this, v)
  def set(v: T)(implicit t: Txn) = update(v)(t)
  
  def id(): Long
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
//  def transactMulti[T](f: Txn => T): T
  def read[T](f: TxnR => T): T
  

  def view(f: TxnR => Unit): Long
  def view(f: TxnR => Unit, exe: ExecutorService, onlyMostRecent: Boolean): Long
}

trait TxnR {
  def get[T](box: Box[T]): T
  def revision(): Revision  
}

trait Txn extends TxnR {
  def create[T](t: T): Box[T]
  def set[T](box: Box[T], t: T): Box[T]
}

trait View


