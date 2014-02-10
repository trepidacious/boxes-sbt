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
import net.liftweb.json._
import scala.xml.NodeSeq
import scala.util.Success
import scala.util.Failure

private case class VersionedValue[T](value: T, index: Long)
private case class VersionedValueAndGUID[T](value: T, index: Long, guid: String)

object AjaxDataLinkView {
  def apply[T](elementId: String, v: String, data: Var[T])(implicit mf: Manifest[T]): AjaxView = new AjaxDataLinkView[T](elementId, v, data)
}

private class AjaxDataLinkView[T](elementId: String, v: String, data: Var[T])(implicit mf: Manifest[T]) extends AjaxView with Loggable {
  
  implicit val formats = DefaultFormats 
  
  //This is accessed only inside a Box transaction, so no additional synchronisation required
  private var clientV = None: Option[T]
  private var clientChangesUpTo = None: Option[Long]  //TODO this could probably just be a boolean, set to true on client changes, false on others in partialUpdates

  val call = SHtml.ajaxCall(JE.JsRaw("1"), (s:String)=>{
    val json = parse(s)
    Try(json.extract[VersionedValue[T]]) match {
      case Success(in) => Box.transact{
                            if (in.value != null && in.index == data.lastChangeIndex || (clientChangesUpTo.getOrElse(data.lastChangeIndex-1) >= data.lastChangeIndex)) {
                              clientV = Some(in.value)
                              data() = in.value
                              clientChangesUpTo = Some(data.lastChangeIndex)
                              clientV = None
                            }
                          }
      case Failure(e) => logger.info("Failed to parse '" + s + "'", e)
    }
  })
      
  val guid = call.guid
  
  //This creates the controller code in Angular on the browser, that will send commits back to us on the named variable
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
      case Some(x) if x == d => Noop
      case _ => {
        clientChangesUpTo = None
        val vvg = VersionedValueAndGUID(data(), data.lastChangeIndex, guid)
        val json = Serialization.write(vvg)
        JE.JsRaw("angular.element('#" + elementId + "').scope().$apply(function ($scope) {$scope." + v + " = " + json + ";});")
      } 
    }
  })
}

