package boxes.lift.demo

import net.liftweb._
import net.liftweb.http._
import net.liftweb.http.SHtml._
import net.liftweb.util._
import net.liftweb.actor._
import net.liftweb.util.Helpers._
import boxes.View
import boxes.Var
import boxes.BoxImplicits._
import com.mongodb.casbah.commons.Imports._
import boxes.lift.comet.AjaxViewImplicits._
import boxes.lift.comet._
import boxes.lift.box._
import boxes.Path
import boxes.Cal


class CometMessage extends CometActor {
  
  val frame = Data.mb.findOne[Frame](MongoDBObject("name" -> "ika1")).getOrElse{
    val ed = Frame()
    ed.name() = "ika1"
    Data.mb.keep(ed)
    ed
  }
  val f = Var(frame)
    
  private val allViews = List(
    AjaxTextView(     "Name",             Path{f().name}),
    AjaxTextView(     "Model",            Path{f().model}),
    AjaxStarsView(    "Hand to hand",     Path{f().handToHand},   Frame.maxSystemsPerType),
    AjaxStarsView(    "Direct fire",      Path{f().direct},       Frame.maxSystemsPerType),
    AjaxStarsView(    "Artillery range",  Path{f().artillery},    Frame.maxSystemsPerType),
    AjaxStarsView(    "Movement",         Path{f().movement},     Frame.maxSystemsPerType),
    AjaxStarsView(    "Comms/sensor",     Path{f().comms},        Frame.maxSystemsPerType),
    AjaxStarsView(    "Defensive",        Path{f().defensive},    Frame.maxSystemsPerType),
    AjaxStringView(   "Systems",          Cal{f().systems()}),
    AjaxStringView(   "Points",           Cal{f().points()})
  )
    
  def render = allViews.flatMap(v => v.render)
                      
  val view = View {
//    allViews.foreach(v => v.noticeChanges)
    this ! ReRender(false)  
  }
}

//case object Update