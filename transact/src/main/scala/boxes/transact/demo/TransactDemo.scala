package boxes.transact

import boxes.transact._

object TransactDemo {

  def thread(f: => Unit) = new Thread(new Runnable{
    def run() = f
  }).start()
  
  def main(args: Array[String]): Unit = {
    val s = ShelfDefault()

    val a = s.create("a")
    val b = s.create("b")

    val view = s.view{
      implicit t: TxnR => {
        println("view, a = " + a() + ", b = " + b())
      }
    }
    
    s.transact{
      implicit t: Txn => {
        println("a = " + a() + ", b = " + b())
        a() = "a2"
        println("a = " + a() + ", b = " + b())
      }
    }

    s.transact{
      implicit t: Txn => {
        println("a = " + a() + ", b = " + b())
        b()= "b2"
        println("a = " + a() + ", b = " + b())
      }
    }

    println(s.read{
      implicit t: TxnR => {
        "read a = " + a() + ", b = " + b()
      }
    })
    

    val x = s.create(0.0)
    val y = s.create(0.0)
    
    Range(0, 5).foreach(i => thread{
      Range(0, 1000).foreach{_ =>
        val a = Math.random() - 0.5
        s.transact{
          implicit t: Txn => {
            x() = x() - a
            y() = y() + a
            Thread.sleep(((a + 0.5) * 2).asInstanceOf[Long])
          }
        }
      }
      println("Thread " + i + " done")
      s.read{
        implicit t: TxnR => {
          println("x = " + x())
          println("y = " + y())
        }
      }
    })
    
    thread{
      Range(0, 1000).foreach{_ =>
        s.read {
          implicit t: TxnR => {
            println("x = " + x() + ", y = " + y())
            Thread.sleep(5)
          }
        }
      }
    }

    
  }
}