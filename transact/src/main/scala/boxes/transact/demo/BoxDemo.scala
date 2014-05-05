package boxes.transact

import boxes.transact._

object BoxDemo {

  def main(args: Array[String]): Unit = {
    implicit val s = ShelfDefault()

    val a = BoxNow(1)
    val b = BoxNow(0)
    b.now << {implicit txn => a() * 2}

    println(b.now())
    a.now() = 2
    println(b.now())
    
  }
}