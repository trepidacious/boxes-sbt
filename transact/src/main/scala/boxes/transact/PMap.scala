package boxes.transact
import scala.language.higherKinds

case class PEntry[K[_], V[_], T](key: K[T], value: V[T])

trait PMap[K[_], V[_]] {
  def get[T](key: K[T]): Option[V[T]]
  def apply[T](key: K[T]) = get[T](key)
  
  def put[T](key: K[T], value: V[T]): PMap[K, V]
  def putAll(entries: List[PEntry[K, V, _]]): PMap[K, V]
}

private class PMapDefault[K[_], V[_]] (val map: Map[K[_], V[_]]) extends PMap[K, V]{
  def get[T](key: K[T]): Option[V[T]] = map.get(key).asInstanceOf[Option[V[T]]]
  def put[T](key: K[T], value: V[T]): PMap[K, V] = new PMapDefault[K, V](map.updated(key, value))
  def putAll(entries: List[PEntry[K, V, _]]): PMap[K, V] = new PMapDefault[K, V](entries.foldLeft(map){case (map, entry) => map.updated(entry.key, entry.value)})
}

object PMap {
  def apply[K[_], V[_]]() = new PMapDefault[K, V](Map.empty): PMap[K, V]
}
