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

object TimesheetView {
    def toNodeSeq(e: TimeEntry) = if (e.in) {
    <span class="label-text label-success"><i class="fa fa-sign-in"></i> In {Timesheet.printInstant(e.time)}</span>
  } else {
    <span class="label-text label-default"><i class="fa fa-sign-out"></i> Out {Timesheet.printInstant(e.time)}</span>
  }

  def toNodeSeqBrief(e: TimeEntry) = if (e.in) {
    <span><span class="label label-success white-space-nowrap"><i class="fa fa-sign-in"></i> {Timesheet.printInstantBrief(e.time)}</span><span> </span></span>
  } else {
    <span><span class="label label-default white-space-nowrap"><i class="fa fa-sign-out"></i> {Timesheet.printInstantBrief(e.time)}</span><span> </span></span>
  }

  def printDuration(duration: Duration) = {
    val hours = duration.toStandardHours().getHours()
    val minutes = duration.toStandardMinutes().getMinutes() - (hours * 60)
    (hours, minutes) match {
      case (0, 0) => "less than a minute"
      case (0, 1) => "about a minute"
      case (0, m) => m + " minutes"
      case (1, 0) => "1 hour"
      case (1, 1) => "1 hr 1 min"
      case (1, m) => "1 hr " + m + " mins"
      case (h, 0) => h + " hrs"
      case (h, 1) => h + " hrs 1 min"
      case (h, m) => h + " hrs " + m + " mins"
    }
  }
  
  def timeElapsedToNodeSeq(millis: Long, e: TimeEntry) = {
    val duration = new Duration(e.time, millis)
    val s = printDuration(duration)
    if (e.in) {
      <span class="label-text label-success"><i class="fa fa-sign-in"></i> In for {s}</span>
    } else {
      <span class="label-text label-default"><i class="fa fa-sign-out"></i> Out for {s}</span>
    }
  }
}

class TimesheetView() extends InsertCometView[Option[Timesheet]](Timesheet.forCurrentUser()){
  
  def makeView(ot: Option[Timesheet]) = {
    
    ot.map(t => AjaxListOfViews(ListVal(
        AjaxTextView(S.?("timesheet.status"),    Path(t.status)),
        AjaxNodeSeqView(S.?("timesheet.current.state"), Cal{
          t.sortedEntries().lastOption.map(e => TimesheetView.timeElapsedToNodeSeq(Timesheet.now(), e)).getOrElse(Text("Never signed in or out"))          
        }),
        AjaxStringView(S.?("timesheet.now"),    Timesheet.nowString),

        AjaxNodeSeqView(S.?("timesheet.recent.list"), Cal{
          //Would be nice to have more than 5, but this wraps badly on small screen
          val midnight = Timesheet.todayMidnight()
          t.sortedEntries().takeRight(5).filter(_.time > midnight).map(e => {TimesheetView.toNodeSeqBrief(e)})
        }, addP=true),
        
        AjaxStringView(S.?("timesheet.time.in.today"),    Cal{
          val ds = t.daySummary(new DateTime(Timesheet.todayMidnight()), Some(Timesheet.now()))
          TimesheetView.printDuration(new Duration(ds.totalIn))
        }),
        
//        AjaxButtonView(S.?("timesheet.in.button"), Val(true), if (!t.in()) S.error(S.?("timesheet.already.signed.in")) else S.notice(S.?("timesheet.signed.in"))),
//        AjaxButtonView(S.?("timesheet.out.button"), Val(true), if (!t.out()) S.error(S.?("timesheet.already.signed.out")) else S.notice(S.?("timesheet.signed.out"))),
        AjaxButtonView(S.?("timesheet.in.button"), Val(true), t.in()),
        AjaxButtonView(S.?("timesheet.out.button"), Val(true), t.out()),
        
        AjaxButtonView("Clear", Val(true), t.clear()),
        
        //Fill out today as a typical day, for demo
        AjaxButtonView("Typical day today", Val(true), {
          val dt = new DateTime()
          val y = dt.year().get()
          val m = dt.monthOfYear().get()
          val d = dt.dayOfMonth().get()
          t.addEntry(TimeEntry(true, new DateTime(y, m, d, 9, 0).toInstant().getMillis()))
          t.addEntry(TimeEntry(false, new DateTime(y, m, d, 12, 30).toInstant().getMillis()))
          t.addEntry(TimeEntry(true, new DateTime(y, m, d, 13, 15).toInstant().getMillis()))
          t.addEntry(TimeEntry(false, new DateTime(y, m, d, 17, 25).toInstant().getMillis()))
        }),

        AjaxButtonView("Typical day yesterday", Val(true), {
          val dt = new DateTime().plusDays(-1)
          val y = dt.year().get()
          val m = dt.monthOfYear().get()
          val d = dt.dayOfMonth().get()
          t.addEntry(TimeEntry(true, new DateTime(y, m, d, 9, 5).toInstant().getMillis()))
          t.addEntry(TimeEntry(false, new DateTime(y, m, d, 12, 25).toInstant().getMillis()))
          t.addEntry(TimeEntry(true, new DateTime(y, m, d, 13, 10).toInstant().getMillis()))
          t.addEntry(TimeEntry(false, new DateTime(y, m, d, 17, 45).toInstant().getMillis()))
        })

    ))).getOrElse(AjaxNodeSeqView(control = Text(S.?("user.no.user.logged.in")))) //TODO S.?
  }

}
