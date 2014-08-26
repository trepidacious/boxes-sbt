package boxes.transact.lift.user.snippet

import scala.xml.Text
import boxes.transact.lift.user.User
import net.liftweb.http.S
import net.liftweb.http.SHtml
import net.liftweb.http.SHtml.ElemAttr.pairToBasic
import net.liftweb.http.js.JsCmds
import net.liftweb.util.BindHelpers._
import boxes.transact.lift.LiftShelf
import boxes.transact.lift.user.Pass
import net.liftweb.common.Loggable

//FIXME could be rewritten like UserSignup, with an AjaxView, if we could get logInFreshSession to work
class UserResetPassword(user: User) extends Loggable {
  
  implicit val shelf = LiftShelf.shelf
  
  def render = {
    var passA: Option[String] = None
    var passB: Option[String] = None
    
    def submit() = {
      val error = shelf.transact(implicit txn => {
        (passA, passB) match {
          case (Some(a), Some(b)) if a==b => {
            User.validatePassword(a) match {
              case Some(error) => Some(error)
              case None => {
                user.validated() = true         //Completing this validates email address
                user.clearValidationToken()
                
                user.passHash() = Some(Pass.hash(a))
                
                user.clearResetPasswordToken()  //If user has logged in, there is no reason they would want to reset password.
                                                //Best to clear the token so that any old reset link in an email will no longer work if found.

                None
              }
            }
          }
          case _ => Some(S.?("user.reset.passwords.incorrect"))
        }
      })
      
      error match {
        case None => {
          User.logInFreshSession(user)    //Log them in
          S.notice(S.?("user.reset.success"))          
        } 
        case Some(e) => S.error(e)
      }
    }
    
    "* *" #> <form class="ajaxview form-horizontal" method="post" action={S.uri}>{
      UserUtils.formRow(
        Text(S.?("user.password.a") + ":"), 
        JsCmds.FocusOnLoad(SHtml.password("", (s) => passA = Some(s), "class"->"form-control"))) ++
      UserUtils.formRow(
        Text(S.?("user.password.b") + ":"), 
        SHtml.password("", (s) => passB = Some(s), "class"->"form-control")) ++
      UserUtils.formRow(
        Text(""), 
        SHtml.submit(S.?("user.reset.button"), submit, "class" -> "btn btn-primary"))
    }</form>
  }

}
