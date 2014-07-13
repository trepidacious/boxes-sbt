package boxes.transact.implicits
import boxes.transact.reaction._
import java.util.concurrent.Executor
import boxes.transact._
import scala.language.implicitConversions

object Implicits {
//  implicit def valueToBox[T](t:T)(implicit shelf: Shelf) = BoxNow(t)
  
  implicit def closureToPathViaOption[T](path : Txn => Option[Box[T]])(implicit s: Shelf) = PathViaOption.now(path)
  implicit def closureToPathToOption[T](path : Txn => Option[Box[Option[T]]])(implicit s: Shelf) = PathToOption.now(path)
  implicit def closureToPathToBox[T](path : Txn => Box[T])(implicit s: Shelf) = PathToBox.now(path)
}

object Includes {
  //Allow omitting an implicit shelf in e.g. shelf.transact
  def now(implicit shelf: Shelf): Revision = shelf.now
  def create[T](t: T)(implicit shelf: Shelf): Box[T] = shelf.create(t)
  def transact[T](f: Txn => T)(implicit shelf: Shelf): T = shelf.transact(f)
  def read[T](f: TxnR => T)(implicit shelf: Shelf): T = shelf.read(f)
  def transactToRevision[T](f: Txn => T)(implicit shelf: Shelf): (T, Revision) = shelf.transactToRevision(f)
  def react(f: ReactorTxn => Unit)(implicit shelf: Shelf): Reaction = shelf.react(f)
  def view(f: TxnR => Unit)(implicit shelf: Shelf): View = shelf.view(f)
  def view(f: TxnR => Unit, exe: Executor, onlyMostRecent: Boolean)(implicit shelf: Shelf): View = shelf.view(f)
  def auto[T](f: Txn => T)(implicit shelf: Shelf): Auto = shelf.auto(f)
  def auto[T](f: Txn => T, exe: Executor, target: T => Unit)(implicit shelf: Shelf): Auto = shelf.auto(f, exe, target)
}
//  def from(min: Box[N, _]) = {v << n.max(min(), v()); v}
//  def to(max: Box[N, _]) = {v << n.min(max(), v()); v}
//  
//  def clip(min: N, max: N) {
//    val value = v()
//    if (n.compare(value, min) < 0) v() = min else if (n.compare(value, max) > 0) v() = max
//  }
//}
//
//object NumericVarImplicits {
//  implicit def var2NumericVar[N: Numeric](v: Var[N]): NumericVar[N] = new NumericVar[N](v)
//}