package boxes.transact.op

import scala.math.{min, max}
import boxes.transact.Box
import boxes.transact.Shelf
import boxes.transact.TxnR
import boxes.transact.BoxNow
import scala.collection.mutable.ListBuffer

object ListOp {

  //TODO must be a better way to do this...
  def swap[T](list: Vector[T], firstI: Int, up: Boolean) = {
    val secondI = firstI + (if(up) -1 else 1)

    val minI = min(firstI, secondI)
    val maxI = max(firstI, secondI)

    val lb = ListBuffer[T]()
    lb.appendAll(list.take(minI))
    lb.append(list(maxI))
    lb.append(list(minI))
    lb.appendAll(list.takeRight(list.size-minI-2))

    lb.toVector
  }
  
  //TODO must be a better way to do this...
  def insert[T](list: Vector[T], insertion: Int, toInsert: Seq[T]) = {
    val lb = ListBuffer(list: _*)
    lb.insert(insertion, toInsert: _*)
    lb.toVector
  }
}

class ListMoveOp[T](l: Box[Vector[T]], i: Box[Option[Int]], val up:Boolean)(implicit shelf: Shelf) extends Op {

  def applicable(implicit txn: TxnR) =
    i() match {
      case None => false
      case Some(someI) => (up && someI > 0) || (!up && someI < l().size-1)
    }

  val canApply = BoxNow.calc{implicit txn => applicable}

  def apply() = shelf.transact(implicit txn => if (applicable) i().foreach(firstI => l() = ListOp.swap(l(), firstI, up)))
}

class ListMultiMoveOp[T](l: Box[Vector[T]], i: Box[Set[Int]], val up:Boolean)(implicit shelf: Shelf) extends Op {

  def applicable(implicit txn: TxnR) =
    if (i().size == 1) {
      val someI = i().head
      (up && someI > 0) || (!up && someI < l().size-1)
    } else {
      false
    }

  val canApply = BoxNow.calc{implicit txn => applicable}

  def apply() = shelf.transact(implicit txn => if (applicable) l() = ListOp.swap(l(), i().head, up))
  
}

class ListAddOp[T](l: Box[Vector[T]], i: Box[Option[Int]], source: => Seq[T])(implicit shelf: Shelf) extends Op {

  val canApply = BoxNow(true)

  def apply() = shelf.transact(implicit txn => {
    val list = l()
    val insertion = i().map(_ + 1).getOrElse(list.size)
    l() = ListOp.insert(list, insertion, source)
  })
}

class ListMultiAddOp[T](l: Box[Vector[T]], i: Box[Set[Int]], source: => Seq[T])(implicit shelf: Shelf) extends Op {

  val canApply = BoxNow(true)

  def apply() = shelf.transact(implicit txn => for (someT <- source) {
    val insertion = if (i().isEmpty) l().size else i().toList.sorted.last + 1
    l() = ListOp.insert(l(), insertion, source)
  })
}

class ListDeleteOp[T](l: Box[Vector[T]], i: Box[Option[Int]], target:T => Unit)(implicit shelf: Shelf) extends Op {

  val canApply = BoxNow.calc(implicit txn => i().isDefined)

  def apply() = shelf.transact(implicit txn => 
    i().foreach(someI => {
      val lb = ListBuffer(l(): _*)
      val t = lb(someI)
      lb.remove(someI)
      l() = lb.toVector
      target(t)
    })
  )
}

class ListMultiDeleteOp[T](l: Box[Vector[T]], i: Box[Set[Int]], target:T => Unit)(implicit shelf: Shelf) extends Op {

  val canApply = BoxNow.calc(implicit txn => !i().isEmpty)

  def apply() = shelf.transact(implicit txn => {
    val indices = i()
    if (!indices.isEmpty) {
      //Need to work backwards to preserve indices
      val sortedIndices = indices.toList.sorted.reverse
      val lb = ListBuffer(l(): _*)
      sortedIndices.foreach(index => {
        val t = lb(index)
        lb.remove(index)
        target(t)
      })
      l() = lb.toVector
    }
  })
}