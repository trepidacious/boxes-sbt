package boxes.transact.fx

import javafx.beans.property.Property
import boxes.transact.Box
import boxes.transact.Shelf
import scala.language.implicitConversions
import javafx.beans.property._

class FoxBindableProperty[T](val f: Property[T])(implicit shelf: Shelf) {
  def <==> (b: Box[T]) = Fox.bind(b, f)
}
class FoxBindableStringProperty(val f: StringProperty)(implicit shelf: Shelf) {
  def <==> (b: Box[String]) = Fox.bind(b, f)
}
class FoxBindableBooleanProperty(val f: BooleanProperty)(implicit shelf: Shelf) {
  def <==> (b: Box[Boolean]) = Fox.bind(b, f)
}
class FoxBindableIntegerProperty(val f: IntegerProperty)(implicit shelf: Shelf) {
  def <==> (b: Box[Int]) = Fox.bind(b, f)
}
class FoxBindableLongProperty(val f: LongProperty)(implicit shelf: Shelf) {
  def <==> (b: Box[Long]) = Fox.bind(b, f)
}
class FoxBindableFloatProperty(val f: FloatProperty)(implicit shelf: Shelf) {
  def <==> (b: Box[Float]) = Fox.bind(b, f)
}
class FoxBindableDoubleProperty(val f: DoubleProperty)(implicit shelf: Shelf) {
  def <==> (b: Box[Double]) = Fox.bind(b, f)
}

object Includes {
  implicit def property2Bindable[T](f: Property[T])(implicit shelf: Shelf) = new FoxBindableProperty(f)

  implicit def stringProperty2Bindable(f: StringProperty)(implicit shelf: Shelf) = new FoxBindableStringProperty(f)
  implicit def booleanProperty2Bindable(f: BooleanProperty)(implicit shelf: Shelf) = new FoxBindableBooleanProperty(f)
  implicit def integerProperty2Bindable(f: IntegerProperty)(implicit shelf: Shelf) = new FoxBindableIntegerProperty(f)
  implicit def longProperty2Bindable(f: LongProperty)(implicit shelf: Shelf) = new FoxBindableLongProperty(f)
  implicit def floatProperty2Bindable(f: FloatProperty)(implicit shelf: Shelf) = new FoxBindableFloatProperty(f)
  implicit def doubleProperty2Bindable(f: DoubleProperty)(implicit shelf: Shelf) = new FoxBindableDoubleProperty(f)
}