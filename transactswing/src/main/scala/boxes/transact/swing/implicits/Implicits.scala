package boxes.transact.swing.implicits

import scala.language.implicitConversions
import boxes.transact.reaction._
import boxes.transact._
import java.util.concurrent.Executor
import boxes.transact.swing.views._
import boxes.util.NumericClass

object Implicits {
//  implicit def closureToPathViaOption[T](path : Txn => Option[Box[T]])(implicit s: Shelf) = PathViaOption.now(path)
//  implicit def closureToPathToOption[T](path : Txn => Option[Box[Option[T]]])(implicit s: Shelf) = PathToOption.now(path)
//  implicit def closureToPathToBox[T](path : Txn => Box[T])(implicit s: Shelf) = PathToBox.now(path)

  implicit def closureToStringViewViaOption(path : Txn => Option[Box[String]])(implicit s: Shelf) = StringOptionView(PathViaOption.now(path))
  implicit def closureToStringViewToOption(path : Txn => Option[Box[Option[String]]])(implicit s: Shelf) = StringOptionView(PathToOption.now(path))
  implicit def closureToStringViewToBox(path : Txn => Box[String])(implicit s: Shelf) = StringView(PathToBox.now(path))

  implicit def closureToBooleanViewViaOption(path : Txn => Option[Box[Boolean]])(implicit s: Shelf) = BooleanOptionView(PathViaOption.now(path))
  implicit def closureToBooleanViewToOption(path : Txn => Option[Box[Option[Boolean]]])(implicit s: Shelf) = BooleanOptionView(PathToOption.now(path))
  implicit def closureToBooleanViewToBox(path : Txn => Box[Boolean])(implicit s: Shelf) = BooleanView(PathToBox.now(path))

  implicit def closureToNumberViewViaOption[N](path : Txn => Option[Box[N]])
    (implicit n: Numeric[N], nc: NumericClass[N], shelf: Shelf) = NumberOptionView(PathViaOption.now(path))
  implicit def closureToNumberViewToOption[N](path : Txn => Option[Box[Option[N]]])
    (implicit n: Numeric[N], nc: NumericClass[N], shelf: Shelf) = NumberOptionView(PathToOption.now(path))
  implicit def closureToNumberViewToBox[N](path : Txn => Box[N])
    (implicit n: Numeric[N], nc: NumericClass[N], shelf: Shelf) = NumberView(PathToBox.now(path))
  
}
