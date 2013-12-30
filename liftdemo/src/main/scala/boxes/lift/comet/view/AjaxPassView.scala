package boxes.lift.comet.view

import scala.language.implicitConversions
import scala.xml.NodeSeq.seqToNodeSeq
import boxes.Ref
import boxes.lift.comet.AjaxView
import boxes.lift.user.PassHash
import boxes.lift.user.User
import net.liftweb.common.Loggable
import net.liftweb.http.S
import net.liftweb.http.SHtml
import net.liftweb.http.SHtml.ElemAttr.pairToBasic
import net.liftweb.http.js.JsCmd.unitToJsCmd
import net.liftweb.http.js.JsCmds.Replace
import net.liftweb.util.Helpers.strToCssBindPromoter
import net.liftweb.util.Helpers.strToSuperArrowAssoc
import SHtml._
import boxes.Var


class AjaxPassView(p: Var[Option[PassHash]]) extends AjaxView with Loggable {
  var oldPass: Option[String] = None
  var newPassA: Option[String] = None
  var newPassB: Option[String] = None
  
  lazy val id = "passview_" + net.liftweb.util.Helpers.nextFuncName

  //FIXME use resources for strings
  def formSubmit() {
    boxes.Box.transact{
      for {
        ph <- p()
        old <- oldPass
        a <- newPassA
        b <- newPassB
      } {
        if (a != b) {
          S.error(S.?("user.change.passwords.mismatch"))
        } else if (!ph.checkPass(old)) {
          S.error(S.?("user.change.current.incorrect"))
        } else if (User.validatePassword(a).isDefined) {
          S.error(User.validatePassword(a).getOrElse("Invalid new password."))
        } else {
          p() = Some(PassHash(a))
          S.notice(S.?("user.password.changed"))
        }
      }      
    }
  }

  def passwordLine(label: String, commit: (String) => Unit) = {
    (
      ("#label" #> (label + ":")) & 
      ("#control" #> SHtml.password("", commit(_)))
    ).apply(AjaxView.formRowXML)
  }
  
  override def partialUpdates = List(() => Replace(id, render))
 
  def render = 
    AjaxView.formWithId(
      passwordLine(S.?("user.password.current"),  s=>oldPass=Some(s)) ++
      passwordLine(S.?("user.password.a"),        s=>newPassA=Some(s)) ++
      passwordLine(S.?("user.password.b"),        s=>newPassB=Some(s)) ++
      (
        ("#label" #> ("")) & 
        ("#control" #> ajaxSubmit(S.?("user.password.change.button"), ()=>formSubmit(), "class" -> "btn btn-primary"))
      ).apply(AjaxView.formRowXML),
      id
    )
    
  def renderMessage(m: String) = <div id={id} class="form-horizontal">{(
                 (".controls [class+]" #> "controls-text") & 
                 ("#label" #> "") & 
                 ("#control" #> m)
               ).apply(AjaxView.formRowXML)}</div>
  
}

object AjaxPassView {
  def apply(p: Var[Option[PassHash]]) = new AjaxPassView(p)
}
