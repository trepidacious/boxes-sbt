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
import org.joda.time.Duration
import boxes.lift.demo.TimeEntry

object TimesheetRecentDaysView {
  
  def printWorkedDuration(millis: Long) = {
    if (millis == 0) {
      ""
    } else {
      TimesheetView.printDuration(new Duration(millis))
    }
  }
  
  def makeDayView(t: Timesheet, daysOffset: Int) = {
    AjaxDirectView(Cal{
      val day = new DateTime(Timesheet.now()).toDateMidnight().plusDays(daysOffset).toDateTime()
      val ds = t.daySummary(day, Some(Timesheet.now()))
      <span>{Timesheet.printDate(day)}</span><span class="pull-right">{printWorkedDuration(ds.totalIn)}</span>
      <div class="progress">
            {ds.intervalLengths.map{case (in, l) =>         
          if (in) {
            <div class={"progress-bar progress-bar-success"} style={"width: " + l*100 + "%"}>
              <span class="sr-only">In for {l*100}% of the day</span>
            </div>                                
          } else {
            <div class={"progress-bar progress-bar-success"} style={"width: 0%; margin-left: " + l*100 + "%"}>
                  <span class="sr-only">Out for {l*100}% of the day</span>
                </div>                
          }
        }}
      </div>
    })
  }
  
}

class TimesheetRecentDaysView() extends InsertCometView[Option[Timesheet]](Timesheet.forCurrentUser()){
  
  def makeView(ot: Option[Timesheet]) = {
    
    ot.map(t => AjaxListOfViews(ListVal(
        TimesheetRecentDaysView.makeDayView(t, 0),
        TimesheetRecentDaysView.makeDayView(t, -1),
        TimesheetRecentDaysView.makeDayView(t, -2),
        TimesheetRecentDaysView.makeDayView(t, -3),
        TimesheetRecentDaysView.makeDayView(t, -4)
    ))).getOrElse(AjaxNodeSeqView(control = Text(S.?("user.no.user.logged.in")))) //TODO S.?
  }

}
