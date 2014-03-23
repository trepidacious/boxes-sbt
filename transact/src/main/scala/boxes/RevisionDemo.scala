package boxes

import scala.language.implicitConversions

object RevisionDemo {

  def doInTry[T](f: (TransactionTry)=>T): T = {
    val r = Revision.first
    implicit val t = new TransactionTryDefault(r)
    f(t)
  }
  
  
  
  def main(args: Array[String]): Unit = {
//    val r = Revision.first
//    implicit val t = new TransactionTryDefault(r)
//    
//    val a = Box("a")
//    val b = Box("b")
//    println("a = " + a())
//    println("b = " + b())
//    a() = "a2"
//    println("a = " + a())
//    println("b = " + b())
    
    doInTry((t: TransactionTry) => {
      implicit val t2 = t
      val a = Box("a")
      val b = Box("b")
      println("a = " + a())
      println("b = " + b())
      a() = "a2"
      println("a = " + a())
      println("b = " + b())
      }
    )
    
  }
}