package boxes.lift.comet

import scala.language.implicitConversions
import scala.util.Try
import boxes._
import net.liftweb.common.Loggable
import net.liftweb.http.SHtml
import net.liftweb.http.js.JE
import net.liftweb.http.js.JsCmd._
import net.liftweb.http.js.JsCmds._
import net.liftweb.json.DefaultFormats
import net.liftweb.json.parse
import scala.xml.NodeSeq

case class LinkDataIn(value: String, index: Long)
case class LinkDataOut(value: String, index: Long, guid: String)

object AjaxDataLinkView {
  def apply(elementId: String, v: String, data: Var[String]) = new AjaxDataLinkView(elementId, v, data)
}

class AjaxDataLinkView(elementId: String, v: String, data: Var[String]) extends AjaxView with Loggable {
  
  implicit val formats = DefaultFormats 
  
  //This is accessed only inside a Box transaction, so no additional synchronisation required
  private var clientV = None: Option[String]
  private var clientChangesUpTo = None: Option[Long]  //TODO this could probably just be a boolean, set to true on client changes, false on others in partialUpdates

  val guid = {
    val call = SHtml.ajaxCall(JE.JsRaw("1"), (s:String)=>{
      logger.info("Got client edit " + s)
      val json = parse(s)
      for (in <- Try(json.extract[LinkDataIn])) {
        Box.transact{
          logger.info(in)
          if (in.index == data.lastChangeIndex || (clientChangesUpTo.getOrElse(data.lastChangeIndex-1) >= data.lastChangeIndex)) {
            clientV = Some(in.value)
            data() = in.value
            clientChangesUpTo = Some(data.lastChangeIndex)
            clientV = None
            logger.info("Accepted")
          } else {
            logger.info("Older than current " + data.lastChangeIndex + " so rejected")            
          }
        }
      }
    })
    call.guid
  }
  
  //This creates the controller code in Angular on the browser, that will send commits back to us
  override def renderHeader = <div boxes-data-link={v}></div>
  
  def render = NodeSeq.Empty
  
  override def partialUpdates = List(() => {
    val d = data()
    //If this change is occurring as a result of the client committing a new value (in clientV), and
    //the value of data() is this new value, then we don't need to send an update to the client - it already
    //has it. This allows for quick repeated commits from a client without interruption from updates sent to
    //the client to confirm the commits. Note that if we get a change to the data that does NOT come from the
    //client, it will be interrupted and updated with that overriding value
    clientV match {
      case Some(d) => Noop
      case _ => {
        clientChangesUpTo = None
        val json = "{'value': '" + data() + "', 'index': " + data.lastChangeIndex + ", 'guid': '" + guid + "'}"
        JE.JsRaw("angular.element('#" + elementId + "').scope().$apply(function ($scope) {$scope." + v + " = " + json + ";});")
      } 
    }
  })
}

