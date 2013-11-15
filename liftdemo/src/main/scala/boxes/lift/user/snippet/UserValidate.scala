package boxes.lift.user.snippet

import boxes.lift.user.User
import net.liftweb.http.S

class UserValidate(t: User){
  
  def render = {
    t.validated() = true
    t.newToken()
    S.notice("Validated user, email " + t.email())
    User.logIn(t)
    S.redirectTo("/")
  }

}
