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


sealed trait PassViewMode

/**
 * Supports creating a password if we have none, or editing any existing password, repeatedly if required
 */
case object PassEditing extends PassViewMode

/**
 * Supports only creating a password, once the password is created a message is displayed stating this has been successful
 */
case object PassCreation extends PassViewMode

/**
 * Supports only resetting the password. The existing password is not required. Once the password is reset a message is
 * displayed stating this has been successful
 */
case object PassReset extends PassViewMode

class AjaxPassView(user: Ref[User], mode: PassViewMode) extends AjaxView with Loggable {
  var oldPass: Option[String] = None
  var newPassA: Option[String] = None
  var newPassB: Option[String] = None
  
  lazy val id = "passview_" + net.liftweb.util.Helpers.nextFuncName

  //FIXME use resources for strings
  def formSubmit() {
    boxes.Box.transact{
      for {
        ph <- if (mode == PassEditing) user().passHash() else Some(PassHash(""))
        old <- if (mode == PassEditing) oldPass else Some("")
        a <- newPassA
        b <- newPassB
      } {
        if (a != b) {
          S.error("New password and repeat do not match.")
        } else if (mode==PassEditing && !ph.checkPass(old)) {
          S.error("Incorrect current password.")
        } else if (mode==PassCreation && !user().passHash().isEmpty) {
          S.error("Password is already set.")
        } else if (User.validatePassword(a).isDefined) {
          S.error(User.validatePassword(a).getOrElse("Invalid new password."))
        } else {
          user().passHash() = Some(PassHash(a))
          mode match {
            case PassCreation => S.notice("Password created.")
            case PassEditing => S.notice("Password changed.")
            case PassReset => {
              //Each reset token can only be used once
              user().clearResetPasswordToken()
              //User email is validated now, since it must have been used to trigger reset
              user().validated() = true
              user().clearValidationToken()              
              S.notice("Password reset, you are now logged in.")
//              //Log them in
//              User.logInFreshSession(user())
            }
          }
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
 
  def renderForm() = 
    AjaxView.formWithId(
      (if (mode == PassEditing) passwordLine(S.?("user.password.current"), s=>oldPass=Some(s)) else Nil) ++
      passwordLine(S.?("user.password.a"),     s=>newPassA=Some(s)) ++
      passwordLine(S.?("user.password.b"),  s=>newPassB=Some(s)) ++
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
  
  def render = {
    mode match {
      case PassCreation => if (user().passHash().isDefined) renderMessage(S.?("user.password.set")) else renderForm()
      case PassEditing => renderForm()
      case PassReset => renderForm()  //TODO after the user has reset the password, we should really prevent further editing, and if possible log the user in at "/"
    }
  }
}

object AjaxPassView {
  def apply(user: Ref[User], mode: PassViewMode = PassEditing) = new AjaxPassView(user, mode)
}
