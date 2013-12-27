package boxes.lift.comet.view

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
import boxes.lift.comet.AjaxView

class AjaxStringView[T](label: Ref[NodeSeq], s: Ref[T], r:((T)=>NodeSeq)) extends AjaxView {
  lazy val id = net.liftweb.util.Helpers.nextFuncName
  def renderLabel = <span id={"label_" + id}>{label()}</span>
  //TODO Get text to line up with label
  def renderText = <span class="inline-string-view" id={"text_" + id}>{r(s())}</span>
  def render = <div id={id} class="form-horizontal">{(
                 (".controls [class+]" #> "controls-text") & 
                 ("#label" #> renderLabel) & 
                 ("#control" #> renderText)
               ).apply(AjaxView.formRowXML)}</div>
  override def partialUpdates = List(() => Replace("label_" + id, renderLabel) & Replace("text_" + id, renderText))
}

object AjaxStringView {
  def apply[T](label: Ref[NodeSeq], s: Ref[T], r:((T)=>NodeSeq) = {(t:T) => Text(t.toString): NodeSeq}): AjaxView = new AjaxStringView(label, s, r)
}
