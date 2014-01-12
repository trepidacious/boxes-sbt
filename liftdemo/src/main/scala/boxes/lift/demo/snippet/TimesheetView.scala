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
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import boxes.lift.demo.TimeEntry

class TimesheetView() extends InsertCometView[Option[Timesheet]](Timesheet.forCurrentUser()){

  def toNodeSeq(e: TimeEntry) = if (e.in) {
    <span class="label-text label-success"><i class="fa fa-sign-in"></i> In from {Timesheet.printInstant(e.time)}</span>
  } else {
    <span class="label-text label-default"><i class="fa fa-sign-out"></i> Out from {Timesheet.printInstant(e.time)}</span>
  }

  def makeView(ot: Option[Timesheet]) = {
    
    
    ot.map(t => AjaxListOfViews(ListVal(
        AjaxTextView(S.?("timesheet.status"),    Path(t.status)),
        AjaxNodeSeqView(S.?("timesheet.current.state"), Cal{
          t.sortedEntries().lastOption.map(toNodeSeq(_)).getOrElse(Text("Never signed in or out"))          
        }),
        AjaxStringView(S.?("timesheet.now"),    Timesheet.nowString),

//        AjaxNodeSeqView(S.?("timesheet.recent.list"), Cal{
//          ou.map(timesheet => {
//            timesheet.entries().map(entry => Text(entry.time().toString()))
//          }).getOrElse(Text("text"))          
//        })
        
        AjaxButtonView(S.?("timesheet.in.button"), Val(true), if (!t.in()) S.error(S.?("timesheet.already.signed.in")) else S.notice(S.?("timesheet.signed.in"))),
        AjaxButtonView(S.?("timesheet.out.button"), Val(true), if (!t.out()) S.error(S.?("timesheet.already.signed.out")) else S.notice(S.?("timesheet.signed.out"))),
        
        AjaxButtonView("Clear", Val(true), t.clear()),

        //TODO make this fill out a day to demonstrate rendering as progress bar
        AjaxButtonView("Typical day today", Val(true), {})

    ))).getOrElse(AjaxNodeSeqView(control = Text(S.?("user.no.user.logged.in")))) //TODO S.?
  }

}
