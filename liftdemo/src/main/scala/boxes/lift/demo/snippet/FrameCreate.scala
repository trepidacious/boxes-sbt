package boxes.lift.demo.snippet
import boxes.list.ListVal
import boxes.Cal
import boxes.Path
import boxes.lift.comet._
import _root_.net.liftweb.util._
import _root_.net.liftweb.common._
import net.liftweb.util.Helpers._
import com.mongodb.casbah.Imports._
import net.liftweb.http.S
import boxes.lift.comet._
import boxes.lift.comet.AjaxViewImplicits._
import boxes.lift.box.Data
import boxes.lift.demo.Frame
import boxes.BoxImplicits.valueToVal
import boxes.lift.comet.view._


class FrameCreate() extends InsertCometView[Frame](new Frame()){

  def makeView(f: Frame) = {
    
    def create() {
      Data.mb.keep(f)
      S.notice("Frame created")
    }
    
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
    AjaxStringView(   "Points",           Cal{f.points()}),
    AjaxButtonView(   "Create frame",     Cal{true}, create())
    ))
    
  }

}
