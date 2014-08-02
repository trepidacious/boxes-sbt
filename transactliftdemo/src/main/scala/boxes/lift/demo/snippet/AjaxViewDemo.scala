package boxes.lift.demo.snippet

import boxes.transact.lift.comet.InsertCometView
import net.liftweb.common.Loggable
import boxes.transact.ShelfDefault
import boxes.transact.BoxNow
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import boxes.transact.lift.comet.AjaxDirectView
import boxes.transact.lift.LiftShelf
import boxes.transact.lift.comet.AjaxDataSourceView
import boxes.transact.lift.comet.AjaxListOfViews

object Stuff {
  implicit val shelf = LiftShelf.shelf
  val time = BoxNow(System.currentTimeMillis().toString)
  private val executor = Executors.newScheduledThreadPool(1)
  
  executor.scheduleAtFixedRate(new Runnable(){
    override def run() = time.now() = System.currentTimeMillis().toString
  }, 1, 1, TimeUnit.SECONDS)   //FIXME make longer - e.g. 1 minute, also would be nice to synchronise to exact minutes (e.g. run every second but only update to whole minute when it changes)

}

class AjaxViewDemo() extends InsertCometView[String]("") with Loggable {
  
  val time = Stuff.time
  
//  def mv(t: Timesheet) = {
//  
//    AjaxListOfViews(
//      AjaxDataLinkView(
//        "DemoCtrl", 
//        "status", 
//        t.status
//      ),
//      AjaxDataLinkView(
//        "DemoCtrl", 
//        "realDate", 
//        realDate
//      ),
//      AjaxDataLinkView.optional(
//        "DemoCtrl", 
//        "optionalDate", 
//        optionalDate
//      ),
//      AjaxButtonView("Log", Val(true), logger.info("LOG!")),
//      AjaxButtonView("Randomise date", Val(true), {
//        date() = Random.nextInt(1000000)
//        logger.info("Randomised date to " + date())
//      }),
//      AjaxStringView("Date", date),
//      AjaxStringView("Real Date", realDate),
//      AjaxStringView("Optional Date", optionalDate),
//      AjaxDataLinkView(
//        "DemoCtrl", 
//        "date", 
//        date
//      )
//
//    )
//  }
  
  def makeView(s: String) = {  
    AjaxListOfViews(
      AjaxDirectView(<span>HI HI HI</span>),
      AjaxDataSourceView("DemoCtrl", "time", time)
    )
  }
}