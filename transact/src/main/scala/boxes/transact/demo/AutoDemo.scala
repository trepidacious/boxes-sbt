package boxes.transact.demo

import boxes.transact._

object AutoDemo {

  def thread(f: => Unit) = new Thread(new Runnable{
    def run() = f
  }).start()
  
  def main(args: Array[String]): Unit = {
    val s = ShelfDefault()

    val counter = s.create(0)
    val slowResult = s.create(0)
    val uninteresting = s.create(0)
    
    val auto = s.auto{
      implicit t: Txn => {
        println("Starting auto")
        Thread.sleep(100)
        slowResult() = counter() * 2
      }
    }
    
    val view = s.view{
      implicit t: TxnR => {
        println("View, counter = " + counter() + ", slowResult = " + slowResult())
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
      println("Finished incrementing counter")
      Range(0, 1000).foreach{_ => {
          s.transact{
            implicit t: Txn => {
              Thread.sleep(1)
              uninteresting() = uninteresting() + 1
            }
          }
        }
      }
      println("Finished incrementing uninteresting")
    }

    //View runs in daemon thread, so we give it time to finish
    Thread.sleep(5000)
    
  }
}