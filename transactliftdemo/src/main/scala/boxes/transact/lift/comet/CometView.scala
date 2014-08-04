package boxes.transact.lift.comet

import net.liftweb.actor._
import net.liftweb.http._
import net.liftweb.common._
import net.liftweb._
import util.Helpers._
import scala.xml.Text
import scala.xml.NodeSeq
import net.liftweb.http.js.JsCmd
import boxes.transact.Shelf
import boxes.transact.View
import boxes.transact.lift.LiftShelf

object CometView {
    val noView = <span></span>
}

case class CreateBoxView(ajaxView: AjaxView)

class CometView extends CometActor with Loggable {

  implicit val shelf = LiftShelf.shelf

  //Start out blank, with nothing to do on view
  private var renderOut:NodeSeq = CometView.noView
  private var views: List[View] = List.empty
  private var ajaxView: Option[AjaxView] = None
  
  def render = renderOut
  
  override def lowPriority = {
    case ajaxView: AjaxView => {
      //Now we can do our first render
      renderOut = shelf.read(implicit txn => ajaxView.render(txn))
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
      views = ajaxView.partialUpdates.map(pu => shelf.view(implicit txn => {
        //Note - need to call apply() here, don't put it in partialUpdate call below, which is a closure so
        //delays calling it until out of the View!
        val js = pu.apply(txn)
        
        //TODO can we batch all the partial update results (by box cycle, or by time) and send in one transfer?
        this.partialUpdate(js)
      })) 
    }
  }
    
  //Time out the comet actor if it hasn't been on a page for 30 seconds
  override def lifespan = Full(30.seconds)
  
  /**
   * This method will be called as part of the shut-down of the actor.  Release any resources here.
   */
  override protected def localShutdown(): Unit = {
    //Unview all views, to avoid unnecessary executions 
    views.foreach(shelf.unview(_))
    views = Nil
    ajaxView = None
  }
}