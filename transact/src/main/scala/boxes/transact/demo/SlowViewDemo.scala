package boxes.transact.demo

import boxes.transact._

object SlowViewDemo {

  def thread(f: => Unit) = new Thread(new Runnable{
    def run() = f
  }).start()
  
  def main(args: Array[String]): Unit = {
    val s = ShelfDefault()

    val counter = s.create(0)
    
    val view = s.view{
      implicit t: TxnR => {
        print("Thinking")
        Range(0, 10).foreach(_ => {
          print(".")
          Thread.sleep(2)
        })
        println(" in view, counter = " + counter())
      }
    }
    
    thread{
      Range(0, 1000).foreach{_ => {
          s.transact{
            implicit t: Txn => {
              Thread.sleep(1)
              counter() = counter() + 1
            }
          }
        }
      }
      println("Finished incrementing")
    }

    //View runs in daemon thread, so we give it time to finish
    Thread.sleep(5000)
    
//
//    val x = s.create(0.0)
//    val y = s.create(0.0)
//    
//    Range(0, 5).foreach(i => thread{
//      Range(0, 1000).foreach{_ =>
//        val a = Math.random() - 0.5
//        s.transact{
//          implicit t: Txn => {
//            x() = x() - a
//            y() = y() + a
//            Thread.sleep(((a + 0.5) * 2).asInstanceOf[Long])
//          }
//        }
//      }
//      println("Thread " + i + " done")
//      s.read{
//        implicit t: TxnR => {
//          println("x = " + x())
//          println("y = " + y())
//        }
//      }
//    })
//    
//    thread{
//      Range(0, 1000).foreach{_ =>
//        s.read {
//          implicit t: TxnR => {
//            println("x = " + x() + ", y = " + y())
//            Thread.sleep(5)
//          }
//        }
//      }
//    }
//    
//    s.transact{
//      implicit t: TransactionTry => {
//        println("x = " + x())
//        println("y = " + y())
//      }
//    }
//    println(string)
    
  }
}