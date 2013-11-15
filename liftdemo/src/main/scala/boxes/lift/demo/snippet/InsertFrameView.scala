package boxes.lift.demo.snippet

import boxes.BoxImplicits.valueToVal
import boxes.list.ListVal
import boxes.Cal
import boxes.Path
import boxes.lift.comet._
import _root_.scala.xml.{NodeSeq, Text}
import _root_.net.liftweb.util._
import _root_.net.liftweb.common._
import _root_.java.util.Date
import Helpers._
import net.liftweb.sitemap.Menu
import com.mongodb.casbah.Imports._
import boxes.lift.comet.AjaxViewImplicits._
import boxes.lift.demo.Frame
import boxes.lift.box.Data

object InsertFrameView {
  def menu = {
    Menu.param[Frame]("Frame", "Frame", 
      s => Data.mb.findOne[Frame](MongoDBObject("name" -> s)), 
      frame => frame.name())
  }
}

class InsertFrameView(f: Frame) extends InsertCometView[Frame](f){
  
  def makeView(f: Frame) = AjaxListOfViews(ListVal(allViews))

  private val allViews = List[AjaxView](
    AjaxTextView(     "Name",             Path{f.name}),
    AjaxTextView(     "Model",            Path{f.model}),
    AjaxStarsView(    "Hand to hand",     Path{f.handToHand},   Frame.maxSystemsPerType),
    AjaxStarsView(    "Direct fire",      Path{f.direct},       Frame.maxSystemsPerType),
    AjaxStarsView(    "Artillery range",  Path{f.artillery},    Frame.maxSystemsPerType),
    AjaxStarsView(    "Movement",         Path{f.movement},     Frame.maxSystemsPerType),
    AjaxStarsView(    "Comms/sensor",     Path{f.comms},        Frame.maxSystemsPerType),
    AjaxStarsView(    "Defensive",        Path{f.defensive},    Frame.maxSystemsPerType),
    AjaxNumberView(   "Defensive",        Path{f.defensive}),
    AjaxStringView(   "Systems",          Cal{f.systems()}),
    AjaxStringView(   "Points",           Cal{f.points()})
  )

}
