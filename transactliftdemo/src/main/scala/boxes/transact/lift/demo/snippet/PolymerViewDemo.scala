package boxes.transact.lift.demo.snippet

import net.liftweb.common.Loggable
import boxes.transact.ShelfDefault
import boxes.transact.BoxNow
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import boxes.transact.lift.LiftShelf
import boxes.transact.lift.comet._

object PolymerStuff {
  implicit val shelf = LiftShelf.shelf
  
  val time = BoxNow(System.currentTimeMillis().toString)
  private val executor = Executors.newScheduledThreadPool(1)
  executor.scheduleAtFixedRate(new Runnable(){
    override def run() = time.now() = System.currentTimeMillis().toString
  }, 1, 1, TimeUnit.SECONDS)   //FIXME make longer - e.g. 1 minute, also would be nice to synchronise to exact minutes (e.g. run every second but only update to whole minute when it changes)

  val text = BoxNow("Text")
}

class PolymerViewDemo() extends InsertCometView[String]("") with Loggable {
  
  val time = PolymerStuff.time
  val text = PolymerStuff.text
  implicit val shelf = LiftShelf.shelf
  
  def makeView(s: String) = {  
    AjaxListOfViews(
      AjaxStaticView(<p>PoymerViewDemo</p>),
      PolymerDataSourceView("my-element", "time", time),
      PolymerDataLinkView("my-element", "text", text)
    )
  }
}