package boxes.lift.user.snippet

import boxes.lift.user.User
import net.liftweb.http.S
import net.liftweb.sitemap.Menu
import boxes.lift.box.Data
import scala.util.matching.Regex

class UserValidate(t: User){
  
  def render = {
    t.validated() = true
    t.clearValidationToken()
    S.notice("Validated user, email " + t.email())
    User.logIn(t)
    S.redirectTo("/")
  }

}
