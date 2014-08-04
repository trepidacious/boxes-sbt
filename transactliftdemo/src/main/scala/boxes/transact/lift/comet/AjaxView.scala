package boxes.transact.lift.comet

import net.liftweb._
import http._
import SHtml._
import net.liftweb.common._
import net.liftweb.common.Box._
import net.liftweb.util._
import net.liftweb.actor._
import net.liftweb.util.Helpers._
import net.liftweb.http.js.JsCmds.{SetHtml}
import scala.xml.Text
import scala.xml.NodeSeq
import com.mongodb.casbah.commons.Imports._
import net.liftweb.http.js.JsCmds._
import net.liftweb.common.Loggable
import net.liftweb.http.js.JsCmd
import net.liftweb.http.js.JE
import scala.language.implicitConversions
import net.liftweb.http.js.JsCmds
import org.joda.time.LocalTime
import net.liftweb.http.js._
import net.liftweb.http.js.JsCmd._
import net.liftweb.http.js.JsCmds._
import net.liftweb.json.DefaultFormats
import net.liftweb.json._
import org.joda.time.DateTime
import org.joda.time.format.ISODateTimeFormat
import scala.util.Try
import boxes.transact._

case object BoxesFormats{
   val formatter = ISODateTimeFormat.dateTime()  
   val formats = DefaultFormats + BoxesJodaDateTimeSerializer
}

//Parse any date string that can be handled by DateTime, and format DateTimes as ISO8601 date and time with millis
case object BoxesJodaDateTimeSerializer extends CustomSerializer[DateTime](format => (
  {
    case JString(s) => Try(new DateTime(s)).getOrElse(throw new MappingException("Invalid date format " + s))
    case JNull => null
  },
  {
    case d: DateTime => JString(BoxesFormats.formatter.print(d))
  }
))

trait AjaxView {
  def renderHeader: NodeSeq = NodeSeq.Empty
  def render(txn: TxnR): NodeSeq
  def partialUpdates: List[(TxnR)=>JsCmd] = List.empty 
}

object AjaxListOfViews {
  def apply(views: List[AjaxView]) = new AjaxListOfViews(views)
  def apply(views: AjaxView*) = new AjaxListOfViews(views.toList)
}

class AjaxListOfViews(views: List[AjaxView]) extends AjaxView {
  override def renderHeader = views.flatMap(_.renderHeader)
  def render(txn: TxnR) = views.flatMap(_.render(txn))
  override val partialUpdates = views.flatMap(_.partialUpdates)
}

object AjaxStaticView {
  def apply(content: NodeSeq): AjaxStaticView = new AjaxStaticView(content)
}

class AjaxStaticView(content: NodeSeq) extends AjaxView {
  override def render(txn: TxnR) = content
  override def partialUpdates = List.empty 
}

class AjaxRedirectView(url: BoxR[Option[String]]) extends AjaxView {
  def render(txn: TxnR) = NodeSeq.Empty 
  override def partialUpdates = List({implicit txn: TxnR => url().map(JsCmds.RedirectTo(_)).getOrElse(Noop)})
}

object AjaxRedirectView{
  def apply(url: BoxR[Option[String]]) = new AjaxRedirectView(url)
}

object AjaxListDataSourceView {
  def apply[T](elementId: String, v: String, list: BoxR[List[T]], renderElement: (T)=>Map[String, String], deleteElement: (T)=>Unit) = new AjaxListDataSourceView(elementId, v, list, renderElement, deleteElement)
}

//TODO this should have a general guid rather than deleteGUID, and the function should accept a parameter: P which is passed to "actionOnElement" rather than just deleteElement. This will look similar
//to an AjaxDataLinkView.
class AjaxListDataSourceView[T](elementId: String, v: String, list: BoxR[List[T]], renderElement: (T)=>Map[String, String], deleteElement: (T)=>Unit) extends AjaxView with Loggable {
  def render(txn: TxnR) = NodeSeq.Empty
  
  def data(implicit txn: TxnR) = {
    val l = list()
    val lines = l.map(t =>{
      val deleteAC = SHtml.ajaxCall(JE.JsRaw("1"), (s:String)=>{
        deleteElement(t)
        //logger.info("Called delete on " + t + " with string '" + s + "'")        
        })
      val r = renderElement(t)
      "{" + r.map{case (field, value) => "'" + field + "': " + value}.mkString(", ") + ", 'deleteGUID': '" + deleteAC.guid + "'}"
    })
    "[" + lines.mkString(", ") + "]"
  }
  
  override def partialUpdates = List(
      {implicit txn: TxnR => {
        val d = data(txn)
        logger.info("Sending " + d)
        JE.JsRaw("angular.element('#" + elementId + "').scope().$apply(function ($scope) {$scope." + v + " = " + d + ";});")
      }}
  )}



