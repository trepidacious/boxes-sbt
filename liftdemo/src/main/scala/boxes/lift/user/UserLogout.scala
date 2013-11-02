package lifttest.snippet

import boxes.lift.user.User
import net.liftweb.http.S

class UserLogout {  
  //TODO I think we can do this without the html and snippet, not sure how ;)
  def render = {
    User.logOut()
    S.redirectTo("/")
  }
}
