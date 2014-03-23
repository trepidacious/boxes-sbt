package boxes

import scala.language.implicitConversions

object RevisionDemo {

  def doInTry[T](f: (TransactionTry)=>T): T = {
    val r = Revision.first
    val t = new TransactionTryDefault(r)
    f(t)
  }

  def main(args: Array[String]): Unit = {
    
    doInTry {
      implicit t: TransactionTry => {
        val a = Box("a")
        val b = Box("b")
        println("a = " + a())
        println("b = " + b())
        a() = "a2"
        println("a = " + a())
        println("b = " + b())
      }
    }
    
  }
}