package boxes.lift.comet.view

import scala.language.implicitConversions
import scala.xml.NodeSeq
import scala.xml.Text

import boxes.Ref
import boxes.lift.comet.AjaxView
import net.liftweb.http.js.JsCmds.Replace
import net.liftweb.util.Helpers.strToCssBindPromoter

class AjaxStringView[T](label: Ref[NodeSeq], s: Ref[T], r:((T)=>NodeSeq)) extends AjaxView {
  lazy val id = net.liftweb.util.Helpers.nextFuncName
  def renderLabel = <span id={"label_" + id}>{label()}</span>
  //TODO Get text to line up with label
  def renderText = <span id={"text_" + id}><p class="form-control-static">{r(s())}</p></span>
  def render = <div id={id} class="form-horizontal">{(
                 ("#label" #> renderLabel) & 
                 ("#control" #> renderText)
               ).apply(AjaxView.formRowXMLWithoutError)}</div>
  override def partialUpdates = List(() => Replace("label_" + id, renderLabel) & Replace("text_" + id, renderText))
}

object AjaxStringView {
  def apply[T](label: Ref[NodeSeq], s: Ref[T], r:((T)=>NodeSeq) = {(t:T) => Text(t.toString): NodeSeq}): AjaxView = new AjaxStringView(label, s, r)
}
