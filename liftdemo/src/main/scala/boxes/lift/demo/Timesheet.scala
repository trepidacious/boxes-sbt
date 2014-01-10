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

case class TimeEntry(in: Boolean, time: Long)

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
}

class Timesheet extends MongoNode{
  val meta = Timesheet
  
  val userId = Var("")
  val entries = ListVar[TimeEntry]()
  val status = Var("No status...")
  
  def addEntry(in: Boolean) {
    entries() = entries() :+ TimeEntry(in, System.currentTimeMillis())
  }
  
  def in() = addEntry(true)
  def out() = addEntry(false)
  
  
}