package boxes.transact

object RevisionDemo {

  def main(args: Array[String]): Unit = {
    val s = Shelf()

    val ab = s.transact{
      implicit t: TransactionTry => {
        val a = Box("a")
        val b = Box("b")
        println("a = " + a())
        println("b = " + b())
        a() = "a2"
        println("a = " + a())
        println("b = " + b())
        (a, b)
      }
    }
    
    s.transact{
      implicit t: TransactionTry => {
        val a = ab._1
        val b = ab._2
        println("a = " + a())
        println("b = " + b())
      }
    }
    
  }
}