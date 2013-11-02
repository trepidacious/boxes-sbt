package lifttest.snippet

import scala.xml.Text

import boxes.lift.user.User
import boxes.lift.comet.AjaxView
import net.liftweb.http.S
import net.liftweb.http.SHtml
import net.liftweb.http.SHtml.ElemAttr.pairToBasic
import net.liftweb.http.js.JsCmds
import net.liftweb.util.Helpers.strToCssBindPromoter
import net.liftweb.util.Helpers.strToSuperArrowAssoc

class UserLogin {
  
  def render = {
    var email = ""
    var password = ""
    def submit() {
      User.findByEmail(email) match {
        case Some(user) if user.validated() && user.checkPass(password) => User.logInFreshSession(user)
        case Some(user) if !user.validated() => S.error(S.?("account.validation.error"))
        case _ => S.error(S.?("invalid.credentials"))
      }
    }
    
    //TODO do this in the Loc instead?
    if (User.loggedIn.isDefined) {
      S.notice(S.?("already.logged.in"))
      S.redirectTo(S.referer openOr "/")
    }
    
    "* *" #> <form class="ajaxview form-horizontal" method="post" action={S.uri}>{
      AjaxView.formRow(
        Text(S.?("email.address") + ":"), 
        JsCmds.FocusOnLoad(SHtml.text("", email = _))) ++
      AjaxView.formRow(
        Text(S.?("password") + ":"), 
        SHtml.password("", password = _)) ++
      AjaxView.formRow(
        Text(""), 
        SHtml.submit(S.?("log.in"), submit, "class" -> "btn primary"))
    }</form>
  }

}
