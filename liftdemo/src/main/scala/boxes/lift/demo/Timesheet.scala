package boxes.lift.demo

import boxes.persistence.mongo.MongoNode
import boxes.persistence.mongo.MongoMetaNode
import boxes.persistence.mongo.MongoNodeIndex
import boxes.persistence.mongo.MongoNodeIndex
import boxes.lift.box.Data
import boxes.lift.user.User
import boxes.list._
import boxes._
import com.mongodb.casbah.commons.Imports._
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.joda.time.format.DateTimeFormat
import org.joda.time.DateTime
import scala.xml.Text
import org.joda.time.DateTimeZone
import DateTimeImplicits._

  //TODO put this somewhere more general, is there one already?
object DateTimeImplicits {
  implicit val DateTimeOrdering = Ordering.by((d: DateTime) =>d.getMillis)  
}

case class TimeEntry(index: Int, in: Boolean, time: DateTime) extends Ordered[TimeEntry] {
  def sortKeys = (time, index, in)
  //TODO why do we need this implicitly and not just sortKeys compare that.sortKeys?
  def compare(that: TimeEntry): Int = implicitly[Ordering[Tuple3[DateTime, Int, Boolean]]].compare(this.sortKeys, that.sortKeys)
}

case class DaySummary(totalIn: Long, intervalLengths: List[(Boolean, Double)])

object Timesheet extends MongoMetaNode {
    
  override def indices = List(MongoNodeIndex("userId", true))
  
  def findByUserId(userId: String) = Data.mb.findOne[Timesheet](MongoDBObject("userId" -> userId))
  def findByUser(user: User) = findByUserId(user.id())
  
  def forCurrentUser() = User.loggedIn.map(user => findByUser(user).getOrElse(apply(user.id())))
  
  private def apply(userId: String) = {
    val t = new Timesheet()
    t.userId() = userId
    Data.mb.keep(t)
    t
  }

  //New DateTime for current time in UTC
  def dateTime() = new DateTime(DateTimeZone.UTC)

  //Private Var is updated regularly to be the current DateTime in UTC
  private val _now = Var(dateTime)  
  private val executor = Executors.newScheduledThreadPool(1)
  executor.scheduleAtFixedRate(new Runnable(){
    override def run() = _now() = dateTime()
  }, 5, 5, TimeUnit.SECONDS)   //FIXME make longer - e.g. 1 minute, also would be nice to synchronise to exact minutes (e.g. run every second but only update to whole minute when it changes)

  //The current DateTime in UTC (to some reasonable granularity for timekeeping, e.g. 1 minute)
  val now = Cal{_now()}

}

class Timesheet extends MongoNode{
  
  val meta = Timesheet
  
  val userId = Var("")
  
  val status = Var("No status...")
  
  //TODO make this private, will this interfere with mongo persistence?
  val nextIndex = Var(1)
  
  /**
   * A list of TimeEntries, in the order the entries were input to the system.
   * The last entry in the list overrides any previous entries with the same exact time in millis.
   * For the time up to but not including the time of the first entry in the list, the user is signed out.
   * For all times after that, the user is in or out at time t according to the "in" field of the entry which has
   * the greatest time that is less than or equal to t. As mentioned above, where there are multiple such entries
   * with the same exact time, the entry that is latest in the entries list is authoritative.
   */
  val entries = ListVar[TimeEntry]()
      
  /**
   * The contents of entries, sorted by entry natural ordering, which is in 
   * increasing order of time, then increasing index, then in before out (this last
   * should never be relevant, since indices are unique).
   * 
   * This means that where there are entries with the same exact
   * time in millis, the one that is added to the list last, with the highest index,
   * will be last in the sortedEntries list. 
   * 
   * This gives the behaviour of being able to non-destructively
   * "overwrite" an entry by adding a new entry with the same exact time but higher index.
   * 
   * It also means we have a canonical answer for whether we are in or out for the
   * time period from that time to the next entry.
   */
  val sortedEntries = Cal{entries().sorted}
  
  //Looking at the docs for sortedEntries, we can see that by reversing the
  //list then finding the first entry that is at exactly time or less, that
  //will be the entry that is currently setting the in/out state at time millis.
  //Since there may be no entry at or before the time, we return an Option.
  def entryAt(time: DateTime) = sortedEntries().reverse.find(!_.time.isAfter(time)) //Entry must be at time or less, so not after.

  //Whether we are signed in at a given time, assuming we are out before first entry
  def inAt(time: DateTime) = entryAt(time).map(_.in).getOrElse(false)

  private def newEntry(in: Boolean, time: DateTime) = {
    Box.transact{
      val index = nextIndex()
      nextIndex() = index + 1
      TimeEntry(index, in, time)
    }
  }
  
  def signInOrOut(in: Boolean) = {
    Box.transact{
      val now = Timesheet.dateTime()
      val inNow = inAt(now);
      
      //If this agrees with the entry we plan to add, don't bother adding the new entry.
      //This avoids adding redundant entries.
      if (inNow != in) {
        entries() = entries() :+ newEntry(in, now)
        true
      } else {
        false
      }
    }
  }
  
  def in() = signInOrOut(true)
  def out() = signInOrOut(false)
  def clear() = entries() = List()
  
  def addEntry(in: Boolean, time: DateTime) {
    if (!time.isAfter(Timesheet.dateTime)) {
      entries() = entries() :+ newEntry(in, time)
    }
  }

  def addLateEntry(in: Boolean, time: DateTime) {
    //FIXME provide a response, and pass through filters to determine
    //whether we will accept the entry and add directly to main entries list,
    //add to a separate list for later approval, or reject straight away
    entries() = entries() :+ newEntry(in, time)
  }

  def daySummary(start: DateTime, now: Option[DateTime]) = {
    val startPlusDay = start.plusDays(1)
    
//    val s = start.toInstant().getMillis()
//    val sPlusDay = end.toInstant().getMillis()
    
    //If now is specified, and is before the end of the day, use it as our end time, otherwise use end of day.
    val end = now match {
      case Some(dt) if dt.isBefore(startPlusDay) => dt
      case _ => startPlusDay
    }
    
    val te = Box.transact {
      //Start with an entry giving state at exact start of interval, then entries that are strictly inside the interval, then out at the exact end of the interval
      val startEntry = TimeEntry(0, inAt(start), start)
      val endEntry = TimeEntry(nextIndex(), false, end)
      startEntry +: sortedEntries().filter(entry => (entry.time.isAfter(start)) && (entry.time.isBefore(end))) :+ endEntry
    }
    
    val pairs = te.zip(te.tail)
    
    //Now scan through the entries, adding up the time worked. We just look at each "in" entry, and count time worked until the next entry
    val totalIn = pairs.map(_ match {
      case (a, b) if a.in => b.time.getMillis() - a.time.getMillis()
      case _ => 0
    }).sum
    
    //Produce a list of intervals to render time worked as a proportion of day. Note we always use the full day, even if we are capping entries with the "now" time
    val length = (startPlusDay.getMillis() - start.getMillis()): Double
    val intervals = pairs.map{case (a, b) => (a.in, (b.time.getMillis() - a.time.getMillis())/length)}
    
    DaySummary(totalIn, intervals)
  }

}