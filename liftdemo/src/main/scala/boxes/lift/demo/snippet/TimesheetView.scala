package boxes.lift.demo.snippet
import boxes.list.ListVal
import boxes.Cal
import boxes.Path
import boxes.lift.comet._
import _root_.net.liftweb.util._
import _root_.net.liftweb.common._
import net.liftweb.util.Helpers._
import com.mongodb.casbah.Imports._
import boxes.lift.comet._
import boxes.lift.comet.AjaxViewImplicits._
import boxes.lift.box.Data
import boxes.lift.demo.Frame
import boxes.BoxImplicits.valueToVal
import net.liftweb.sitemap.Menu
import net.liftweb.common.Box.option2Box
import boxes.lift.comet.view._
import boxes.lift.demo.Timesheet
import scala.xml.Text
import net.liftweb.http.S
import boxes.Val
import boxes.lift.demo.TimeEntry

class TimesheetView() extends InsertCometView[Option[Timesheet]](Timesheet.forCurrentUser()){

  def makeView(ou: Option[Timesheet]) = {
    ou.map(t => AjaxListOfViews(ListVal(
        AjaxTextView(S.?("timesheet.status"),    Path(t.status)),
        AjaxButtonView(S.?("timesheet.in.button"), Val(true), ou.foreach(_.in())),
        AjaxButtonView(S.?("timesheet.out.button"), Val(true), ou.foreach(_.out())),
        AjaxNodeSeqView(S.?("timesheet.recent.list"), Cal{
          ou.map(timesheet => {
            timesheet.entries().map(entry => Text(entry.time().toString()))
          }).getOrElse(Text("text"))
          
        })
    ))).getOrElse(AjaxNodeSeqView(control = Text(S.?("user.no.user.logged.in")))) //TODO S.?
  }

}
