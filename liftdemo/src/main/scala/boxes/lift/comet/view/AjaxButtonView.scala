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
import boxes.Box
import net.liftweb.http.SHtml.ElemAttr
import net.liftweb.http.js.JsCmds.SetElemById
import net.liftweb.http.js.JE
import boxes.lift.comet.AjaxLabelledView
import boxes.lift.comet.AjaxOffsetView
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
  
  def offset(label: Ref[NodeSeq], enabled: Ref[Boolean], action: => Unit, buttonClass: ButtonClass = DefaultButton) = AjaxOffsetView(AjaxButtonView(label, enabled, action, buttonClass))

//  def withAttrs(label: Ref[NodeSeq], enabled: Ref[Boolean], action: => Unit, buttonClass: ButtonClass, attrs: ElemAttr*) = new AjaxButtonView(label, enabled, action, buttonClass, attrs:_*)

  def bootstrapClass(c: ButtonClass) = c match {
    case DefaultButton => "btn-default"
    case PrimaryButton => "btn-primary"
    case SuccessButton => "btn-success"
    case InfoButton =>    "btn-info"
    case WarningButton => "btn-warning"
    case DangerButton =>  "btn-danger"
    case LinkButton =>    "btn-link"
  }
}

class AjaxButtonView(label: Ref[NodeSeq], enabled: Ref[Boolean], action: => Unit, buttonClass: ButtonClass, attrs: ElemAttr*) extends AjaxView {
  lazy val id = "button_" + net.liftweb.util.Helpers.nextFuncName

  //Make sure that we only ever execute the action while we are sure the button should be enabled.
  def onClick() {
    Box.transact{
      if (enabled()) {
        action
      }
    }
  }
  
  val enabledAttr = List[ElemAttr]("id" -> id, "class" -> ("btn " + AjaxButtonView.bootstrapClass(buttonClass))) ++ attrs.toList
  val disabledAttr = List[ElemAttr]("disabled" -> "disabled") ++ enabledAttr
  
  def render = {
    //If we actually disable the button, it can cause problems since there may be a delay enabling the button
    //when it is valid. E.g. we might enable it when all input is correct, but this might only occur when the
    //last input box loses focus and sends the new data, then the browser receives the partial update some time later.
    //In the meantime the user could well have tried to click the button and failed. Better to just make the button
    //fail to do anything when clicked, as it does now.
    //val attr = if (enabled()) enabledAttr else disabledAttr
    val attr = enabledAttr;
    ajaxButton(label(), () => {onClick; Noop}, attr:_*)      
  }

  override def partialUpdates = List(
    () => Replace(id, render)
  )
}
