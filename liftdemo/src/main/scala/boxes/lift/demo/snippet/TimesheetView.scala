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
import boxes.Var
import org.joda.time.LocalTime
import boxes.Box
import net.liftweb.http.SHtml
import net.liftweb.http.js.JE
import net.liftweb.http.js.JsCmd
import boxes.lift.demo.TimeEntry
import net.liftweb.json._
import scala.util.Random
import java.util.Date

object TimesheetView {
  def toNodeSeq(e: TimeEntry) = if (e.in) {
    <span class="timestamp-large timestamp-in"><i class="fa fa-sign-in"></i> In {Timesheet.printInstant(e.time)}</span>
  } else {
    <span class="timestamp-large timestamp-out"><i class="fa fa-sign-out"></i> Out {Timesheet.printInstant(e.time)}</span>
  }

  def toNodeSeqBrief(e: TimeEntry) = if (e.in) {
    <span><span class="timestamp timestamp-in"><i class="fa fa-sign-in"></i> {Timesheet.printInstantBrief(e.time)}</span><span> </span></span>
  } else {
    <span><span class="timestamp timestamp-out"><i class="fa fa-sign-out"></i> {Timesheet.printInstantBrief(e.time)}</span><span> </span></span>
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
      <span class="timestamp-large timestamp-in"><i class="fa fa-sign-in"></i> In for {s}</span>
    } else {
      <span class="timestamp-large timestamp-out"><i class="fa fa-sign-out"></i> Out for {s}</span>
    }
  }
  
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
  
  val dateEntryFormat = DateTimeFormat.forPattern("yyyy-MM-dd");
  val timeEntryFormat = DateTimeFormat.forPattern("HH:mm");
  def parseDateEntry(s: String): Option[DateTime] = {
    try {
      Some(dateEntryFormat.parseDateTime(s))
    } catch {
      case _:IllegalArgumentException => None
    }
  }
  def parseTimeEntry(s: String): Option[LocalTime] = {
    try {
      Some(LocalTime.parse(s, timeEntryFormat))
    } catch {
      case _:IllegalArgumentException => None
    }
  }
}

class TimesheetView() extends InsertCometView[Option[Timesheet]](Timesheet.forCurrentUser()){
  
  def makeView(ot: Option[Timesheet]) = {
    
    ot.map(t => AjaxListOfViews(List(
        AjaxTextView(S.?("timesheet.status"),    Path(t.status)),
        
        AjaxNodeSeqView(S.?("timesheet.current.state"), Cal{
          t.sortedEntries().lastOption.map(e => TimesheetView.timeElapsedToNodeSeq(Timesheet.now(), e)).getOrElse(Text("Never signed in or out"))          
        }),
//        AjaxStringView(S.?("timesheet.now"),    Timesheet.nowString),

        AjaxNodeSeqView(S.?("timesheet.today.list"), Cal{
          //Would be nice to have more than 5, but this wraps badly on small screen
          val midnight = Timesheet.todayMidnight()
          t.sortedEntries().takeRight(5).filter(_.time > midnight).map(e => {TimesheetView.toNodeSeqBrief(e)})
        }, addP=true)
        
//        AjaxStringView(S.?("timesheet.time.in.today"),    Cal{
//          val ds = t.daySummary(new DateTime(Timesheet.todayMidnight()), Some(Timesheet.now()))
//          TimesheetView.printDuration(new Duration(ds.totalIn))
//        })
    ))).getOrElse(AjaxNodeSeqView(control = Text(S.?("user.no.user.logged.in"))))
  }
}

class TimesheetRecentDaysView() extends InsertCometView[Option[Timesheet]](Timesheet.forCurrentUser()){  
  def makeView(ot: Option[Timesheet]) = {
    ot.map(t => AjaxListOfViews((-4 to 0).reverse.map(TimesheetView.makeDayView(t, _)).toList
        )).getOrElse(AjaxNodeSeqView(control = Text(S.?("user.no.user.logged.in"))))
  }
}

class TimesheetButtonsView() extends InsertCometView[Option[Timesheet]](Timesheet.forCurrentUser()){
  
  def makeView(ot: Option[Timesheet]) = {
    val dateEntry = Var("")
    val timeEntry = Var("")
    val modalName = "lateInOutModal"
    
    val date = Cal{TimesheetView.parseDateEntry(dateEntry())}
    val time = Cal{TimesheetView.parseTimeEntry(timeEntry())}
    
    val dateError = Cal{if (date().isEmpty) Some(S.?("timesheet.dateentry.error")) else None}
    val timeError = Cal{if (time().isEmpty) Some(S.?("timesheet.timeentry.error")) else None}

    val dateTime = Cal{
      for (t <- time(); d <- date()) yield d.plusMillis(t.getMillisOfDay())
    }

    def resetLate() {
      dateEntry() = TimesheetView.dateEntryFormat.print(new DateTime().toDateMidnight().toDateTime())
      timeEntry() = TimesheetView.timeEntryFormat.print(new LocalTime())
    }
    
    def late(in: Boolean) {
      Box.transact{
        dateTime() match {
          case Some(dt) => {
            ot.foreach(t => {
              t.addLateEntry(TimeEntry(in, dt.toInstant().getMillis()))
              S.notice("Added late " + (if (in) "in" else "out") +" at " + Timesheet.printDate(dt))
            })
          }
          case None => {
            S.error(S.?("timesheet.late.failed"))
            dateError().foreach(S.error(_))
            timeError().foreach(S.error(_))
          }
        }
      }
    }
    
    def modal(t: Timesheet) = 
      AjaxModalView(
        AjaxListOfViews(
          AjaxTextView("Date", dateEntry, dateError),
          AjaxTextView("Time", timeEntry, timeError)
        ),
        AjaxListOfViews(
          AjaxButtonView.dismissModal(<span> <i class="fa fa-sign-in"></i> {S.?("timesheet.late.in.button")} </span>, Val(true), late(true), SuccessButton),
          AjaxButtonView.dismissModal(<span> <i class="fa fa-sign-out"></i> {S.?("timesheet.late.out.button")} </span>, Val(true), late(false), DefaultButton)
        ),
        "Late in/out",
        modalName
      )

    ot.map(t => 
      AjaxListOfViews(
        AjaxLabelledView.nodeSeq(S.?("timesheet.action.buttons"), 
          AjaxButtonToolbar(
            //FIXME make the buttons disabled when already signed in/out respectively. Would be useful
            //to be able to make buttons just appear disabled, but still accept clicks, since we already
            //reject clicks at the server side when the view is disabled.
            AjaxButtonGroup(
              AjaxButtonView(<span> <i class="fa fa-sign-in"></i> {S.?("timesheet.in.button")} </span>, Val(true), t.in(), SuccessButton),
              AjaxButtonView(<span> <i class="fa fa-sign-out"></i> {S.?("timesheet.out.button")} </span>, Val(true), t.out(), DefaultButton)
            ),
            
            AjaxButtonGroup(
              AjaxButtonView.showModal(<span> <i class="fa fa-exclamation"></i> {S.?("timesheet.late.button")} </span>, 
                  Val(true), resetLate, WarningButton, modalName)
            )
          )
        ),
        modal(t)
      )
    ).getOrElse(AjaxNodeSeqView(control = Text(S.?("user.no.user.logged.in"))))
  }
}

class AngularTimesheetTable() extends InsertCometView[Option[Timesheet]](Timesheet.forCurrentUser()) with Loggable{
  
  def log(s: String): JsCmd = {
    logger.info(s)
  }
  
  def renderEntry(e: TimeEntry) = {
    Map("in" -> e.in.toString, "time" -> e.time.toString)
  }

  def makeView(ot: Option[Timesheet]) = {  
    ot.map(t => AjaxListDataSourceView(
        "DemoCtrl", 
        "entries", 
        Cal{t.entries()},
        renderEntry,
        //TODO better delete, should have an id or similar so it can't delete multiple entries that happen to have same in+time
        (delete: TimeEntry) => t.entries()=t.entries().filter(_!=delete))).getOrElse(AjaxNodeSeqView(control = Text(S.?("user.no.user.logged.in"))))
  }
}

class AngularTestString() extends InsertCometView[Option[Timesheet]](Timesheet.forCurrentUser()) with Loggable{
  
  val date = Var(42L)
  val realDate = Var(new DateTime())
  val optionalDate = Var(None:Option[DateTime])
  
  def mv(t: Timesheet) = {
  
    AjaxListOfViews(
      AjaxDataLinkView(
        "DemoCtrl", 
        "status", 
        t.status
      ),
      AjaxDataLinkView(
        "DemoCtrl", 
        "realDate", 
        realDate
      ),
      AjaxDataLinkView.optional(
        "DemoCtrl", 
        "optionalDate", 
        optionalDate
      ),
      AjaxButtonView("Log", Val(true), logger.info("LOG!")),
      AjaxButtonView("Randomise date", Val(true), {
        date() = Random.nextInt(1000000)
        logger.info("Randomised date to " + date())
      }),
      AjaxStringView("Date", date),
      AjaxStringView("Real Date", realDate),
      AjaxStringView("Optional Date", optionalDate),
      AjaxDataLinkView(
        "DemoCtrl", 
        "date", 
        date
      )

    )
  }
  
  def makeView(ot: Option[Timesheet]) = {  
    ot.map(mv(_)).getOrElse(AjaxNodeSeqView(control = Text(S.?("user.no.user.logged.in"))))
  }
}

class TimesheetDebugButtonsView() extends InsertCometView[Option[Timesheet]](Timesheet.forCurrentUser()){
  
  def makeView(ot: Option[Timesheet]) = {
    
    ot.map(t => AjaxOffsetButtonGroup(List(
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

    ))).getOrElse(AjaxNodeSeqView(control = Text(S.?("user.no.user.logged.in"))))
  }

}
