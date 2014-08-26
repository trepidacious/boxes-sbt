package boxes.transact.lift.user.snippet

import boxes.transact.lift.user.User
import net.liftweb.http.S
import boxes.transact.lift.LiftShelf

class UserValidate(u: User){
  implicit val shelf = LiftShelf.shelf
  
  //Just rendering this page validates the user, since we can only get to it by providing a
  //user id and matching validation token
  def render = {
    val email = shelf.transact(implicit txn => {
      u.validated() = true
      u.clearValidationToken()
      u.email()
    })
    S.notice("Validated user, email " + email)
    User.logIn(u)
    S.redirectTo("/")
  }

}
