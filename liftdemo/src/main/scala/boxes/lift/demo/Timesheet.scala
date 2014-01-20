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

case class TimeEntry(in: Boolean, time: Long)

case class DaySummary(totalIn: Long, intervalLengths: List[(Boolean, Double)])

object Timesheet extends MongoMetaNode {
  override def indices = List(MongoNodeIndex("userId", true))
  
  def findByUserId(userId: String) = Data.mb.findOne[Timesheet](MongoDBObject("userId" -> userId))
  def findByUser(user: User) = findByUserId(user.id())
  
  def forCurrentUser() = User.loggedIn.map(user => findByUser(user).getOrElse(apply(user.id())))
  
  def apply(userId: String) = {
    val t = new Timesheet()
    t.userId() = userId
    Data.mb.keep(t)
    t
  }

  val dateTimeFormat = DateTimeFormat.forPattern("EEEE, d MMMM HH:mm:ss");
//  val dateTimeFormatBrief = DateTimeFormat.forPattern("d MMM HH:mm:ss");
  val dateTimeFormatBrief = DateTimeFormat.forPattern("HH:mm");
  val dateTimeFormatDate = DateTimeFormat.forPattern("EEEE, d MMMM");

  private val millis = Var(System.currentTimeMillis())
  
  val now = Cal{millis()}

  val todayMidnight = Cal{new DateTime(millis()).toDateMidnight().getMillis()}

  val nowString = Cal{printInstant(millis())}

  private val executor = Executors.newScheduledThreadPool(1)
  executor.scheduleAtFixedRate(new Runnable(){
    override def run() = millis() = System.currentTimeMillis()
  }, 20, 20, TimeUnit.SECONDS)

  def printInstant(millis: Long) = dateTimeFormat.print(millis)
  def printInstantBrief(millis: Long) = dateTimeFormatBrief.print(millis)
  def printDate(dateTime: DateTime) = dateTimeFormatDate.print(dateTime)
    
}

class Timesheet extends MongoNode{
  val meta = Timesheet
  
  val userId = Var("")
  
  val status = Var("No status...")
  
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
   * The contents of entries, sorted to be in increasing order of time.
   * This is a stable sort, so where there are entries with the same exact
   * time in millis, the one that is last in the entries list will be last
   * in the sortedEntries list. Since entries are added at the end of the
   * entries list, the entry actually added most recently will appear
   * last in sortedEntries. This gives the behaviour of being able to non-destructively
   * "overwrite" an entry by adding a new entry with the same exact time.
   * It also means we have a canonical answer for whether we are in or out for the
   * time period from that time to the next entry.
   */
  val sortedEntries = Cal{entries().sortWith((a, b) => a.time < b.time)}
  
  //Looking at the docs for sortedEntries, we can see that by reversing the
  //list then finding the first entry that is at exactly time millis or less, that
  //will be the entry that is currently setting the in/out state at time millis.
  def entryAt(millis: Long) = sortedEntries().reverse.find(_.time <= millis)

  //Whether we are signed in at a given time
  def inAt(millis:Long) = entryAt(millis).map(_.in).getOrElse(false)

  def signInOrOut(in: Boolean) = {
    Box.transact{
      val millis = System.currentTimeMillis()
      val inAtMillis = inAt(millis);
      
      //If this agrees with the entry we plan to add, don't bother adding the new entry.
      //This avoids adding redundant entries.
      if (inAtMillis != in) {
        entries() = entries() :+ TimeEntry(in, millis)
        true
      } else {
        false
      }
    }
  }
  
  def in() = signInOrOut(true)
  def out() = signInOrOut(false)
  def clear() = entries() = List()
  
  def addEntry(e: TimeEntry) {
    if (e.time <= System.currentTimeMillis()) {
      entries() = entries() :+ e
    }
  }

  def addLateEntry(e: TimeEntry) {
    //FIXME provide a response, and pass through filters to determine
    //whether we will accept the entry and add directly to main entries list,
    //add to a separate list for later approval, or reject straight away
    entries() = entries() :+ e
  }

  def daySummary(start: DateTime, now: Option[Long]) = {
    val end = start.plusDays(1)
    
    val s = start.toInstant().getMillis()
    val sPlusDay = end.toInstant().getMillis()
    
    val e = now match {
      case Some(millis) if millis < sPlusDay => millis
      case _ => sPlusDay
    }
    
    val te = Box.transact {
      //Start with an entry giving state at exact start of interval, then entries that are strictly inside the interval, then out at the exact end of the interval
      TimeEntry(inAt(s), s) +: sortedEntries().filter(entry => (entry.time > s) && (entry.time < e)) :+ TimeEntry(false, e)
    }
    
    val pairs = te.zip(te.tail)
    
    //Now scan through the entries, adding up the time worked. We just look at each "in" entry, and count time worked until the next entry
    val totalIn = pairs.map(_ match {
      case (a, b) if a.in => b.time - a.time
      case _ => 0
    }).sum
    
    //Produce a list of intervals to render time worked as a proportion of day. Note we always use the full day, even if we are capping entries with the "now" time
    val length = (sPlusDay - s): Double
    val intervals = pairs.map{case (a, b) => (a.in, (b.time - a.time)/length)}
    
    DaySummary(totalIn, intervals)
  }

}