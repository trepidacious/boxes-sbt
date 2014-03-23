package boxes

import java.util.concurrent.locks.ReentrantReadWriteLock

class Box[T] {
  def apply()(implicit t: TransactionTry): T = t.get(this)
  def get()(implicit t: TransactionTry): T = apply()(t)
  
  def update(v: T)(implicit t: TransactionTry) = t.set(this, v)
  def set(v: T)(implicit t: TransactionTry) = update(v)(t)
}

object Box {
  def apply[T](v: T)(implicit t: TransactionTry): Box[T] = t.create(v)
}

trait Writes {
  def get[T](box: Box[T]): Option[T]
  def put[T](box: Box[T], value: T): Writes
  def boxes: Iterable[Box[_]]
}

private class WritesDefault(val map: Map[Box[_], _]) extends Writes {
  def get[T](box: Box[T]) = map.get(box).asInstanceOf[Option[T]]
  def put[T](box: Box[T], value: T): Writes = new WritesDefault(map.updated(box, value))
  def boxes: Iterable[Box[_]] = map.keys
}

object Writes {
  val empty = new WritesDefault(Map.empty): Writes
}

case class State[T](revision: Long, value: T)

trait Revision {
  val index: Long
  
  def stateOf[T](box: Box[T]): Option[State[T]]
  def indexOf(box: Box[_]): Option[Long]
  def valueOf[T](box: Box[T]): Option[T]
  
  def apply[T](box: Box[T]) = stateOf[T](box)
  
  def write(writes: Writes): Revision
}

private class RevisionDefault(val index: Long, map: Map[Box[_], State[_]]) extends Revision {

  def stateOf[T](box: Box[T]): Option[State[T]] = map.get(box).asInstanceOf[Option[State[T]]]
  def indexOf(box: Box[_]): Option[Long] = map.get(box).map(_.revision)
  def valueOf[T](box: Box[T]): Option[T] = stateOf(box).map(_.value)
  
  def write(writes: Writes): Revision = {
    val newIndex = index + 1
    //TODO kind of messy - can we avoid the extra option, since we know that writes contains
    //the box as a key... Would like to just iterate (key, value) instead
    val newMap = writes.boxes.foldLeft(map){case (map, box) => writes.get(box) match {
      case Some(value) => map.updated(box, State(newIndex, value))
      case None => map
    }}
    new RevisionDefault(newIndex, newMap)
  } 
}

object Revision {
  val first = new RevisionDefault(0, Map.empty): Revision
}

case class Commit(revisionIndex: Long, reads: Set[Box[_]], writes: Writes)

trait Shelf {
  def commit(c: Commit): Option[Revision]
  def now: Revision
}

private class ShelfDefault extends Shelf {
  private val lock = new ReentrantReadWriteLock

  private var current = Revision.first

//  def apply[T](box: Box[T])(implicit r: Revision) = r.values.get(box).getOrElse(throw new RuntimeException("Box does not exist in current revision")).data
  
  def commit(c: Commit) = {
    val start = c.revisionIndex
    lock.writeLock().lock()
    try {
      //If we any boxes that were read have changed, we fail
      if (c.reads.iterator.flatMap(current.indexOf(_)).exists(_>start)) {
        None
      //Same for writes - note that newly created boxes will have no index, so will not fail
      } else if (c.writes.boxes.flatMap(current.indexOf(_)).exists(_>start)) {
        None
      } else {
        Some(current.write(c.writes))
      }
    } finally {
      lock.writeLock().unlock()
    }
  }
  
  def now = {
    lock.readLock().lock()
    try {
      current
    } finally {
      lock.readLock().unlock()
    }
  } 
}

object Shelf {
  def apply(): Shelf = new ShelfDefault
}

trait TransactionTry {
  def create[T](t: T): Box[T]
  def set[T](box: Box[T], t: T): Box[T]
  def get[T](box: Box[T]): T
}

class TransactionTryDefault(val r: Revision) extends TransactionTry {
  private val lock = new ReentrantReadWriteLock

  private def write[T](w: =>T): T = {
    lock.writeLock().lock()
    try {
      return w
    } finally {
      lock.writeLock().unlock()
    }
  }

  private def read[T](r: =>T): T = {
    lock.readLock().lock()
    try {
      return r
    } finally {
      lock.readLock().unlock()
    }
  }
  
  private var writes = Writes.empty
  
  def create[T](t: T): Box[T] = write{
    val b = new Box[T]
    writes = writes.put(b, t)
    b
  }
  
  def set[T](box: Box[T], t: T): Box[T] = write{
    writes = writes.put(box, t)
    box
  }
  def get[T](box: Box[T]): T = read{writes.get(box).getOrElse(r.valueOf(box).getOrElse(throw new RuntimeException("Missing Box")))}
}



