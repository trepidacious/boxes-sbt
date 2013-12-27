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

object FrameEdit {
  def menu = {
    Menu.param[Frame]("FrameEdit", "FrameEdit", 
      s => Data.mb.findOne[Frame](MongoDBObject("name" -> s)), 
      frame => frame.name())
  }
}

class FrameEdit(f: Frame) extends InsertCometView[Frame](f){

  def makeView(f: Frame) = {
    
    AjaxListOfViews(ListVal(
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
    ))
    
  }

}
