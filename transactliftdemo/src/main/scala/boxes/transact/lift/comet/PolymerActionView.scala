package boxes.transact.lift.comet
import boxes.transact._
import net.liftweb.common.Loggable
import net.liftweb.http.SHtml
import net.liftweb.http.js.JE
import net.liftweb.http.js.JsCmd._
import net.liftweb.http.js.JsCmds._
import net.liftweb.json.DefaultFormats
import net.liftweb.json._
import scala.xml.NodeSeq
import scala.util.Try
import scala.util.Success
import scala.util.Failure

//TODO use something like the header to make assignment of action to scope instant - can we just put a script in here? This would mean the buttons could work BEFORE the comet sends its partial update.
object PolymerActionView {
  /**
   * Create a PolymerActionView
   * @param selector The id of the element in the page for the Polymer component.
   * @param v         The value to set within the scope. This value will be set to the guid  of the action, and so javascript on the client will be able to use it
   *                  to invoke the action
   * @param action    The action to invoke with the guid, using a parameter supplied from the client
   */
  def apply[T](selector: String, v: String, action: (T)=>Unit)(implicit mfj: Manifest[T]): AjaxView = new PolymerActionView[T, T](selector, v, identity, action)
  
  /**
   * Create an AjaxAngularActionView using an optional value, where javascript null is converted to None, and a value is converted to Some(value)
   * @param selector The id of the element in the page for the Polymer component.
   * @param v         The value to set within the scope. This value will be set to the guid  of the action, and so javascript on the client will be able to use it
   *                  to invoke the action
   * @param action    The action to invoke with the guid, using a parameter supplied from the client
   */
  def optional[T](selector: String, v: String, action: (Option[T])=>Unit)(implicit mfj: Manifest[T]): AjaxView = {
    def toT[T](t: T) = if (t == null) None else Some(t)
    new PolymerActionView[Option[T], T](selector, v, toT, action)
  }
  
  /**
   * Create an AjaxAngularActionView using no value, just invoking a =>Unit when the guid is called
   * @param selector The id of the element in the page for the Polymer component.
   * @param v         The value to set within the scope. This value will be set to the guid  of the action, and so javascript on the client will be able to use it
   *                  to invoke the action
   * @param action    The action to invoke with the guid
   */
  def apply(selector: String, v: String)(action: =>Unit): AjaxView = new PolymerUnitActionView(selector, v, action)
}

private class PolymerActionView[T, J](selector: String, v: String, toT: (J)=>T, action: (T)=>Unit)(implicit mfj: Manifest[J]) extends AjaxView with Loggable {
  
  implicit val formats = BoxesFormats.formats

//  override def renderHeader = <script type="text/javascript">{actionSetter()}</script>
  
  override def render(txn: TxnR) = NodeSeq.Empty

  val call = SHtml.ajaxCall(JE.JsRaw("1"), (s:String)=>{
    val json = parse(s)
    Try(json.extract[J]) match {
      case Success(inJ) => {
        val in = toT(inJ)
        if (in != null) {
          action(in)
        }
      }
      case Failure(e) => logger.info("Failed to parse '" + s + "'", e)
    }
  })
  
  def actionSetter() = "jQuery(document).ready(function() {" + actionRaw() + "});"

  def actionRaw() = "document.querySelector('" + selector + "').dataFromServer('" + v + "', {'isGUID':true, 'guid':'" + call.guid + "'});"
  
  override def partialUpdates = List({implicit txn: TxnR => JE.JsRaw(actionRaw())})
}

private class PolymerUnitActionView(selector: String, v: String, action: =>Unit) extends AjaxView with Loggable {
  
//  override def renderHeader = <script type="text/javascript">{actionSetter()}</script>

  override def render(txn: TxnR) = NodeSeq.Empty
  
  val call = SHtml.ajaxCall(JE.JsRaw("1"), (s:String)=>{
    logger.info("Action called!")
    action
    })
    
  def actionSetter() = "jQuery(document).ready(function() {" + actionRaw() + "});"
  def actionRaw() = "document.querySelector('" + selector + "').dataFromServer('" + v + "', {'isGUID':true, 'guid':'" + call.guid + "'});"

  override def partialUpdates = List({implicit txn: TxnR => JE.JsRaw(actionRaw())})
}
