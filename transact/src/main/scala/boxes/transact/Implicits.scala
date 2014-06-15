package boxes.transact

import scala.language.implicitConversions
import boxes.transact.reaction._

object Implicits {
//  implicit def closureToPathViaOption[T](path: Txn => Option[Box[T]])(implicit shelf: Shelf) = PathViaOption.now(path)
//  implicit def closureToPath[T](path: Txn => Box[T])(implicit shelf: Shelf) = Path.now(path)
//  implicit def closureToPathToOption[T](path: Txn => Option[Box[Option[T]]])(implicit shelf: Shelf) = PathToOption.now(path)
  
  implicit def closureToPathViaOption[T](path: Txn => Option[Box[T]])(implicit txn: Txn) = PathViaOption(path)
  implicit def closureToPath[T](path: Txn => Box[T])(implicit txn: Txn) = Path(path)
  implicit def closureToPathToOption[T](path: Txn => Option[Box[Option[T]]])(implicit txn: Txn) = PathToOption(path)
  
  implicit def valueToBox[T](t:T)(implicit shelf: Shelf) = BoxNow(t)
}

//class NumericVar[N](v: Var[N])(implicit n: Numeric[N]) {
//  
//  def from(min: N) = {v << n.max(min, v()); v}
//  def to(max: N) = {v << n.min(max, v()); v}
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