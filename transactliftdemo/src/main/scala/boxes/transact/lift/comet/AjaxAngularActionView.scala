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
object AjaxAngularActionView {
  def apply[T](elementId: String, v: String, action: (T)=>Unit)(implicit mfj: Manifest[T]): AjaxView = new AjaxAngularActionView[T, T](elementId, v, identity, action)
  
  def optional[T](elementId: String, v: String, action: (Option[T])=>Unit)(implicit mfj: Manifest[T]): AjaxView = {
    def toT[T](t: T) = if (t == null) None else Some(t)
    new AjaxAngularActionView[Option[T], T](elementId, v, toT, action)
  }
  
  def apply(elementId: String, v: String)(action: =>Unit): AjaxView = new AjaxAngularUnitActionView(elementId, v, action)
}

private class AjaxAngularActionView[T, J](elementId: String, v: String, toT: (J)=>T, action: (T)=>Unit)(implicit mfj: Manifest[J]) extends AjaxView with Loggable {
  
  implicit val formats = BoxesFormats.formats

  def render(txn: TxnR) = NodeSeq.Empty

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
  
  override def partialUpdates = List({implicit txn: TxnR => JE.JsRaw("angular.element('#" + elementId + "').scope().$apply(function ($scope) {$scope." + v + " = '" + call.guid + "';});")})
}

private class AjaxAngularUnitActionView(elementId: String, v: String, action: =>Unit) extends AjaxView with Loggable {
  def render(txn: TxnR) = NodeSeq.Empty
  val call = SHtml.ajaxCall(JE.JsRaw("1"), (s:String)=>{
    logger.info("Action called!")
    action
    })
  override def partialUpdates = List({implicit txn: TxnR => JE.JsRaw("angular.element('#" + elementId + "').scope().$apply(function ($scope) {$scope." + v + " = '" + call.guid + "';});")})
}