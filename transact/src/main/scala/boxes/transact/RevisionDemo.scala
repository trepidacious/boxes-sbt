package boxes.transact

object RevisionDemo {

  def main(args: Array[String]): Unit = {
    val s = Shelf()

    val a = s.create("a")
    val b = s.create("b")

    s.transact{
      implicit t: TransactionTry => {
        println("a = " + a())
        println("b = " + b())
        a() = "a2"
        println("a = " + a())
        println("b = " + b())
      }
    }
    
    val string = s.transact{
      implicit t: TransactionTry => {
        println("a = " + a())
        println("b = " + b())
        a() + ", " + b()
      }
    }
    
    println(string)
    
  }
}