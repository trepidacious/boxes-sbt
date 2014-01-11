package boxes.lift.user.snippet

import scala.xml.Text
import boxes.lift.user.User
import boxes.lift.comet.AjaxView
import net.liftweb.http.S
import net.liftweb.http.SHtml
import net.liftweb.http.SHtml.ElemAttr.pairToBasic
import net.liftweb.http.js.JsCmds
import net.liftweb.util.BindHelpers._

//FIXME could be rewritten like UserSignup, with an AjaxView, if we could get logInFreshSession to work
class UserResetPassword(user: User) {
  
  def render = {
    var passA: Option[String] = None
    var passB: Option[String] = None
    
    def submit() {
      (passA, passB) match {
        case (Some(a), Some(b)) if a==b => {
          User.validatePassword(a) match {
            case Some(error) => S.error(error)
            case None => {
              user.validated() = true         //Completing this validates email address
              user.clearValidationToken()
              
              user.clearResetPasswordToken()  //If user has logged in, there is no reason they would want to reset password.
                                              //Best to clear the token so that any old reset link in an email will no longer work if found.
              
              User.logInFreshSession(user)    //Log them in
              S.notice(S.?("user.reset.success"))
            }
          }
        }
        case _ => S.error(S.?("user.reset.passwords.incorrect"))
      }
    }
    
    "* *" #> <form class="ajaxview form-horizontal" method="post" action={S.uri}>{
      AjaxView.formRow(
        Text(S.?("user.password.a") + ":"), 
        JsCmds.FocusOnLoad(SHtml.password("", (s) => passA = Some(s), "class"->"form-control"))) ++
      AjaxView.formRow(
        Text(S.?("user.password.b") + ":"), 
        SHtml.password("", (s) => passB = Some(s), "class"->"form-control")) ++
      AjaxView.formRow(
        Text(""), 
        SHtml.submit(S.?("user.reset.button"), submit, "class" -> "btn btn-primary"))
    }</form>
  }

}
