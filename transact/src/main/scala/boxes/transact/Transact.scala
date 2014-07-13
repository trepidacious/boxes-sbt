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
import java.util.concurrent.Executor

trait Identifiable {
  def id(): Long
}

/**
 * Wraps a Box, and provides the same interface but using an implicit Shelf rather 
 * than an implicit Txn in each case, and executing each corresponding Box method
 * in a single transaction. This can make user code less verbose by removing the
 * explicit transactions creation for transactions that consist of only a single
 * Box method call.
 * Each Box provides a BoxNow wrapping it, in "now" val.
 */
class BoxNow[T](box: Box[T]) {
  def apply()(implicit s: Shelf): T = s.read(implicit txn => txn.get(box))
  def update(v: T)(implicit s: Shelf) = s.transact(implicit txn => txn.set(box, v))

  def get()(implicit s: Shelf): T = apply()(s)
  def set(v: T)(implicit s: Shelf) = update(v)(s)

  def retainReaction(r: Reaction)(implicit s: Shelf) = s.transact{t => t.boxRetainsReaction(box, r)}
  def releaseReaction(r: Reaction)(implicit s: Shelf) = s.transact{t => t.boxReleasesReaction(box, r)}
  
  def << (r: ReactorTxn => T)(implicit s: Shelf) = {
    s.transact(implicit t => {
      val reaction = t.createReaction(implicit rtxn=>{
        box.update(r(rtxn))
      })
      box.retainReaction(reaction)
      reaction
    })
  }
}

trait BoxR[+T] extends Identifiable {
  def apply()(implicit t: TxnR): T = t.get(this)
  def get()(implicit t: TxnR): T = apply()(t)
  
  def retainReaction(r: Reaction)(implicit t: Txn) = t.boxRetainsReaction(this, r)
  def releaseReaction(r: Reaction)(implicit t: Txn) = t.boxReleasesReaction(this, r)
}

trait Box[T] extends BoxR[T] {
  def update(v: T)(implicit t: Txn) = t.set(this, v)

  def set(v: T)(implicit t: Txn) = update(v)(t)
  
  def << (r: ReactorTxn => T)(implicit t: Txn) = {
    val reaction = t.createReaction(implicit rtxn=>{
      Box.this.update(r(rtxn))
    })
    Box.this.retainReaction(reaction)
    reaction
  }
  
  lazy val now = new BoxNow(this)
}

object Box {
  def apply[T](v: T)(implicit t: Txn): Box[T] = t.create(v)
  
  /**
   * Create a Box with a value set by the given reaction
   */
  def calc[T](r: Txn => T)(implicit txn: Txn) = {
    //Create a box with initial value given by running the reaction now, in a normal transaction
    val b = txn.create(r(txn))
    //Now add the reaction to the box
    val reaction = txn.createReaction(implicit rtxn => b() = r(rtxn))
    b.retainReaction(reaction)
    b
  }
}

object BoxNow {
  def apply[T](v: T)(implicit s: Shelf): Box[T] = s.transact{t => t.create(v)}
  
  /**
   * Create a Box with a value set by the given reaction
   */
  def calc[T](r: Txn => T)(implicit s: Shelf) = {
    s.transact(implicit txn => {
      //Create a box with initial value given by running the reaction now, in a normal transaction
      val b = txn.create(r(txn))
      //Now add the reaction to the box
      val reaction = txn.createReaction(implicit rtxn => b() = r(rtxn))
      b.retainReaction(reaction)
      b
    })
  }
}

trait Reaction extends Identifiable

case class State[T](revision: Long, value: T)

trait Revision {
  val index: Long
  
  def stateOf[T](box: BoxR[T]): Option[State[T]]
  def indexOf(box: BoxR[_]): Option[Long]
  def valueOf[T](box: BoxR[T]): Option[T]
  
  def apply[T](box: BoxR[T]) = stateOf[T](box)
}

trait Shelf {
  def now: Revision
  
  def create[T](t: T): Box[T]

  def transact[T](f: Txn => T): T
  def read[T](f: TxnR => T): T

  def transactToRevision[T](f: Txn => T): (T, Revision)
  
  def react(f: ReactorTxn => Unit): Reaction

  def view(f: TxnR => Unit): View
  def view(f: TxnR => Unit, exe: Executor, onlyMostRecent: Boolean): View
  
  def auto[T](f: Txn => T): Auto  
  def auto[T](f: Txn => T, exe: Executor, target: T => Unit): Auto
}

trait TxnR {
  def get[T](box: BoxR[T]): T
  def revision(): Revision  
}

trait Txn extends TxnR {
  def create[T](t: T): Box[T]
  def set[T](box: Box[T], t: T): Box[T]
  
  def createReaction(f: ReactorTxn => Unit): Reaction
  
  def boxRetainsReaction(box: BoxR[_], r: Reaction)
  def boxReleasesReaction(box: BoxR[_], r: Reaction)
  
  def failEarly(): Unit
}

trait View

trait Auto

class TxnEarlyFailException(msg: String = "") extends Exception

private class TxnMulti(txn: Txn) extends Txn{
  val lock = RWLock()
  override def create[T](t: T): Box[T] = lock.write{txn.create(t)}
  override def set[T](box: Box[T], t: T): Box[T] = lock.write{txn.set(box, t)}
  override def createReaction(f: ReactorTxn => Unit) = lock.write{txn.createReaction(f)}

  override def boxRetainsReaction(box: BoxR[_], r: Reaction) = lock.write{txn.boxRetainsReaction(box, r)}
  override def boxReleasesReaction(box: BoxR[_], r: Reaction) = lock.write{txn.boxReleasesReaction(box, r)}

  override def get[T](box: BoxR[T]): T = lock.read{txn.get(box)}
  override def revision() = txn.revision()
  override def failEarly() = txn.failEarly()
}

/** This can be used to wrap a Txn to make it safe for concurrent multi-threaded
  * access, by using a RWLock(). 
  */
object TxnMulti {
  def apply(txn: Txn): Txn = new TxnMulti(txn)
}

class BoxException(message: String = "") extends Exception(message)
class FailedReactionsException(message: String = "") extends BoxException(message)
class ConflictingReactionException(message: String = "") extends BoxException(message)
class ReactionAppliedTooManyTimesInCycle(message: String = "") extends BoxException(message)
