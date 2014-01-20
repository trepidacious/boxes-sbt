package boxes.lift.user.snippet

import scala.xml.Text
import boxes.lift.user.User
import boxes.lift.comet.AjaxView
import net.liftweb.http.S
import net.liftweb.http.SHtml
import net.liftweb.http.SHtml.ElemAttr.pairToBasic
import net.liftweb.http.js.JsCmds
import net.liftweb.util.BindHelpers._

//FIXME could be rewritten like UserSignup, with an AjaxView
class UserLogin {
  
  val hAndP = S.hostAndPath
  
  def render = {
    var email = ""
    var password = ""
    def submit() {
      User.findByEmail(email) match {
        //If validated and password correct, log in
        case Some(user) if user.validated() && user.checkPass(password) => {
          user.clearResetPasswordToken()  //If user has logged in, there is no reason they would want to reset password.
                                          //Best to clear the token so that any old reset link in an email will no longer work if found.
          User.logInFreshSession(user)
        }
        
        //If not yet validated, inform user and send a fresh validation email
        case Some(user) if !user.validated() && user.checkPass(password) => {
          S.error(S.?("user.login.before.validation"))
          User.sendValidationEmail(hAndP, user);
        }
        
        //If password is incorrect, tell user
        case _ => S.error(S.?("invalid.credentials"))
      }
    }

    def reset() {
      email.trim() match {
        case "" => S.error(S.?("user.reset.missing.email"))
        case e => User.findByEmail(e) match {
          case Some(user) => {
            S.notice(S.?("user.reset.email.sent"))
            User.sendResetEmail(hAndP, user)
          }
          case _ => S.error(S.?("user.reset.unknown.email") + e)
        } 
      }
      
    }

    //TODO do this in the Loc instead?
    if (User.loggedIn.isDefined) {
      S.notice(S.?("already.logged.in"))
      S.redirectTo(S.referer openOr "/")
    }
    
    "* *" #> <form class="ajaxview form-horizontal" method="post" action={S.uri}>{
      AjaxView.formRow(
        Text(S.?("user.email") + ":"), 
        JsCmds.FocusOnLoad(SHtml.text("", email = _, "class"->"form-control"))) ++
      AjaxView.formRow(
        Text(S.?("password") + ":"), 
        SHtml.password("", password = _, "class"->"form-control")) ++
      AjaxView.formRow(
        Text(""), 
        <div class="btn-toolbar">
          <div class="btn-group">
            {SHtml.submit(S.?("log.in"), submit, "class" -> "btn btn-primary")}
          </div>
          <div class="btn-group">
            {SHtml.submit(S.?("user.lost.password.button"), reset, "class" -> "btn btn-info")}
          </div>
        </div>
      )
    }</form>
  }

}
