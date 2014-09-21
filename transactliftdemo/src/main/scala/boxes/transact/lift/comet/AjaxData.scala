package boxes.transact.lift.comet

import scala.language.implicitConversions
import scala.util.Try

import net.liftweb.json.Serialization
import net.liftweb.json.parse

case class VersionedValue[T](value: T, index: Long)
case class VersionedValueAndGUID[T](value: T, index: Long, guid: String)

trait JsonTransformer[T] {
  def toJson(t: VersionedValueAndGUID[T]): String
  def toJson(t: VersionedValue[T]): String
  def fromJson(s: String): Try[VersionedValue[T]]
}

class DefaultJsonTransformer[T](implicit m: Manifest[T]) extends JsonTransformer[T] {
  implicit val formats = BoxesFormats.formats
  def toJson(t: VersionedValueAndGUID[T]) =  Serialization.write(t)
  def toJson(t: VersionedValue[T]) =  Serialization.write(t)
  def fromJson(s: String) = Try(parse(s).extract[VersionedValue[T]])
}

object DefaultJsonTransformer{
  def apply[T] = new DefaultJsonTransformer
}
