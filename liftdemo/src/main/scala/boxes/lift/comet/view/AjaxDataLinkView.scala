package boxes.lift.comet

import net.liftweb._
import http._
import SHtml._
import net.liftweb.common._
import net.liftweb.common.Box._
import net.liftweb.util._
import net.liftweb.actor._
import net.liftweb.util.Helpers._
import net.liftweb.http.js.JsCmds.{SetHtml}
import boxes.View
import scala.xml.Text
import boxes.Var
import boxes.Path
import boxes.Val
import boxes.Ref
import scala.xml.NodeSeq
import boxes.BoxImplicits._
import boxes.Cal
import boxes.util.NumericClass
import boxes.persistence.mongo.MongoBox
import boxes.persistence.ClassAliases
import com.mongodb.casbah.commons.Imports._
import boxes.list.ListRef
import boxes.lift.user.PassHash
import boxes.Box
import net.liftweb.http.js.JsCmds._
import net.liftweb.common.Loggable
import net.liftweb.http.js.JsCmd
import boxes.Reaction
import net.liftweb.http.js.JE
import scala.language.implicitConversions
import boxes.lift.user.User
import net.liftweb.http.js.JsCmds
import boxes.lift.comet.view.AjaxButtonView
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.joda.time.LocalTime
import net.liftweb.http.js._
import net.liftweb.json._
import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonDSL._
import scala.util.Try

case class LinkDataIn(value: String, index: Long)
case class LinkDataOut(value: String, index: Long, guid: String)

object AjaxDataLinkView {
  def apply(elementId: String, v: String, data: Var[String]) = new AjaxDataLinkView(elementId, v, data)
}

class AjaxDataLinkView(elementId: String, v: String, data: Var[String]) extends AjaxView with Loggable {
  
  lazy val id = net.liftweb.util.Helpers.nextFuncName
  implicit val formats = DefaultFormats 

  val guid = {
    val call = SHtml.ajaxCall(JE.JsRaw("1"), (s:String)=>{
      logger.info("Got client edit " + s)
      val json = parse(s)
      for (in <- Try(json.extract[LinkDataIn])) {
        Box.transact{
          logger.info(in)
          if (in.index == data.lastChangeIndex) {
            data() = in.value
            logger.info("Accepted")
          } else {
            logger.info("Older than current " + data.lastChangeIndex + " so rejected")            
          }
        }
      }
    })
    call.guid
  }
  
  def render = AjaxView.form(<span id={"data_source_" + id}></span>)
  
  override def partialUpdates = List(() => {
    val json = "{'value': '" + data() + "', 'index': " + data.lastChangeIndex + ", 'guid': '" + guid + "'}"
    JE.JsRaw("angular.element('#" + elementId + "').scope().$apply(function ($scope) {$scope." + v + " = " + json + ";});")
  })
}

