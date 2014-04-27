package boxes.transact

import boxes.transact._

object ReactionDemo {

  def main(args: Array[String]): Unit = {
    val s = ShelfDefault()

    println(">Adding a")
    val a = s.create("a")
    println(">Adding b")
    val b = s.create("b")
    println(">Adding c")
    val c = s.create("")
    
    println(">Adding reaction")
    s.transact{
      implicit txn => {
        txn.createReaction{implicit txn => {
          c() = a() + ", " + b()
        }}
      }
    }

    println(">Printing c")
    s.transact{
      implicit txn => {
        println("c = '" + c() + "'")
      }
    }
    
    println(">Setting a = 'a2' and printing c")
    s.transact{
      implicit txn => {
        a() = "a2"
        println("c = '" + c() + "'")
      }
    }

    println(">Printing c")
    s.transact{
      implicit txn => {
        println("c = '" + c() + "'")
      }
    }

  }
}