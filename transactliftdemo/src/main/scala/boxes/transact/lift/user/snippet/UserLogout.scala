package boxes.transact.lift.user.snippet

import boxes.transact.lift.user.User
import net.liftweb.http.S
import boxes.transact.lift.LiftShelf

class UserLogout {
  //TODO I think we can do this without the html and snippet, not sure how ;)
  def render = {
    User.logOut()
    S.redirectTo("/")
  }
}
