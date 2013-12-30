package boxes.lift.comet.view

import scala.language.implicitConversions
import scala.xml.NodeSeq

import boxes.Ref
import boxes.lift.comet.AjaxView
import net.liftweb.http.SHtml.ElemAttr.pairToBasic
import net.liftweb.http.SHtml.ajaxButton
import net.liftweb.http.js.JsCmds.Noop
import net.liftweb.http.js.JsCmds.Replace
import net.liftweb.util.Helpers.strToSuperArrowAssoc

sealed trait ButtonClass

case object DefaultButton extends ButtonClass
case object PrimaryButton extends ButtonClass
case object SuccessButton extends ButtonClass
case object InfoButton extends ButtonClass
case object WarningButton extends ButtonClass
case object DangerButton extends ButtonClass
case object LinkButton extends ButtonClass

object AjaxButtonView {
  def apply(label: Ref[NodeSeq], enabled: Ref[Boolean], action: => Unit, buttonClass: ButtonClass = DefaultButton) = new AjaxButtonView(label, enabled, action, buttonClass)
  def bootstrapClass(c: ButtonClass) = c match {
    case DefaultButton => ""
    case PrimaryButton => "btn-primary"
    case SuccessButton => "btn-success"
    case InfoButton => "btn-info"
    case WarningButton => "btn-warning"
    case DangerButton => "btn-danger"
    case LinkButton => "btn-link"
  }
}

//FIXME implement "enabled"
class AjaxButtonView(label: Ref[NodeSeq], enabled: Ref[Boolean], action: => Unit, buttonClass: ButtonClass) extends AjaxView {
  lazy val id = "button_" + net.liftweb.util.Helpers.nextFuncName
  
  def render = {
    <div id={id} class="form-horizontal">
      {ajaxButton(label(), () => {action; Noop}, "class" -> ("btn " + AjaxButtonView.bootstrapClass(buttonClass)))}
    </div>
  }

  override def partialUpdates = List(() => Replace(id, render))
}
