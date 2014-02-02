package boxes.lift.comet

import net.liftweb.actor._
import boxes.View
import net.liftweb.http._
import net.liftweb.common._
import net.liftweb._
import util.Helpers._
import scala.xml.Text
import scala.xml.NodeSeq
import boxes.Var
import boxes.Reaction
import net.liftweb.http.js.JsCmd

object CometView {
    val noView = <span>Loading...</span>
}

case class CreateBoxView(ajaxView: AjaxView)

class CometView extends CometActor with Loggable {

  //Start out blank, with nothing to do on view
  private var renderOut:NodeSeq = CometView.noView
  private var views: List[Reaction] = List.empty
  private var ajaxView: Option[AjaxView] = None
  
  def render = renderOut
  
  override def lowPriority = {
    case ajaxView: AjaxView => {
      //Now we can do our first render
      renderOut = ajaxView.render
      this ! ReRender(false)
      
      //FIXME note that the partialUpdates may not be set up as views until
      //AFTER we have some changes to the viewed data, and so they must be
      //full updates to the rendered data, rather than incremental. It would
      //be nice to be able to send incremental updates in some cases...
      
      //Then when we are sure we have done first render,
      //start doing partial updates as a View
      this ! CreateBoxView(ajaxView)
    }
    
    case CreateBoxView(ajaxView) => {
      //Each of the partial updates of the ajaxView should be made into
      //a View, which will call the function when needed, yielding the 
      //actual partial update this CometActor should issue. This allows
      //for fine grained updating of the view using multiple different
      //parts
      this.ajaxView = Some(ajaxView)
      views = ajaxView.partialUpdates.map(pu => View{
        //Note - need to call apply() here, don't put it in partialUpdate call below, which is a closure so
        //delays calling it until out of the View!
        val js = pu.apply()
        
        //TODO can we batch all the partial update results (by box cycle, or by time) and send in one transfer?
        this.partialUpdate(js)
      }) 
    }
  }
    
  //Time out the comet actor if it hasn't been on a page for 2 minutes
  override def lifespan = Full(120.seconds)
}