package boxes.transact.demo

import boxes.transact._
import boxes.transact.data._

object LedgerDemo {
  
  class Person(
      val name: Box[String], 
      val age: Box[Int],
      val friend: Box[Option[Person]])
      
  object Person {
    def apply()(implicit s: Shelf) = s.transact(implicit txn => new Person(Box("Unnamed"), Box(20), Box(None)))
  }
  
  def ledger() {
    
    implicit val s = ShelfDefault()

    val p = Person()
    p.name.now() = "p"
    val q = Person()
    q.name.now() = "q"

    val list = BoxNow(List(p, q, q, p))

    val view = LensRecordView[Person](
      MBoxLens("Name", _.name),
      MBoxLens("Age", _.age)
    )
    
    val ledger = s.transact(implicit txn => ListLedgerBox(list, view))

    s.read(implicit txn => {
      for (f <- 0 until ledger().fieldCount) {
        print(ledger().fieldName(f) + "\t")
      }
      println()
      for (f <- 0 until ledger().fieldCount) {
        print(ledger().fieldClass(f) + "\t")
      }
      println()
      for (r <- 0 until ledger().recordCount) {
        for (f <- 0 until ledger().fieldCount) {
          print(ledger().apply(r, f) + "\t")
        }
        println()
      }      
    })
  }
  
  def main(args: Array[String]) {
    ledger
  }
}