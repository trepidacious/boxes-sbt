package boxes

import scala.collection.immutable.HashMap
import java.util.concurrent.locks.ReentrantReadWriteLock


//case class ID[T](id: Long)

trait Box[T] {
  def apply(): T
  def get() = apply()
  def id(): Long
  def shelf: Shelf
}

trait Val[T] extends Box[T]

trait Var[T] extends Box[T] {
  def update(t: T)
  def set(t: T) = update(t)
}

class Shelf {
  val lock = new ReentrantReadWriteLock
  private var versions = HashMap[Box[_], Long]()
}

class Transact {

}