package boxes.transact.op
import java.awt.Image
import boxes.transact.Box
import boxes.transact.Shelf
import boxes.transact.BoxNow

trait Op {
  def apply(): Unit
  def canApply: Box[Boolean]
}

private class OpDefault(action: => Unit, val canApply: Box[Boolean]) extends Op {
  def apply() {
    action    //Note that just using the name of action applies that function, no () required.
  }
}

object Op {
  //Make sure to provide the action properly - if you have a function, pass as function() not just function
  def apply(action: => Unit, canApply:Box[Boolean]) = new OpDefault(action, canApply): Op
}

object TrueOp {
  def apply[T](v: Box[Boolean], canApply:Box[Boolean])(implicit shelf: Shelf) = new TrueOp[T](v, canApply)
  def apply[T](v: Box[Boolean])(implicit shelf: Shelf) = new TrueOp[T](v, BoxNow(true))
}

class TrueOp[T](v: Box[Boolean], val canApply:Box[Boolean])(implicit shelf: Shelf) extends Op {
  def apply() = shelf.transact(implicit txn => v() = true)
}

object SetOp {
  def apply[T](v: Box[T], s:Box[T])(implicit shelf: Shelf) = new SetOp[T](v, s)
}

class SetOp[T](v: Box[T], s: Box[T])(implicit shelf: Shelf) extends Op {
  def apply() = shelf.transact(implicit txn => v() = s())  
  val canApply = BoxNow.calc(implicit txn => v() != s())
}