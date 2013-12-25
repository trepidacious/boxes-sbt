package boxes.lift.user.snippet

import boxes.lift.user.User
import net.liftweb.http.S
import net.liftweb.sitemap.Menu
import boxes.lift.box.Data
import scala.util.matching.Regex

object UserValidate {
  //Split into two groups. Each group can contain anything but "-", "-" separates the groups.
  //First group is the object id of the user, second group is the token provided in the URL, 
  //which much match the user's current token to trigger validation
  val regex = new Regex("""([^-]+)-([^-]+)""", "id", "token")
  
  def menu = {
    Menu.param[User]("UserValidate", "UserValidate", 
      s => s match {
        case regex(id, token) => Data.mb.findById[User](id).filter(_.token() == Some(token))
        case _ => None
      },
      //FIXME do we need to provide a valid implementation here? It should never be used...
      user => Data.mb.keep(user).toStringMongod + "-" + user.token()) 
  }
}

class UserValidate(t: User){
  
  def render = {
    t.validated() = true
    t.newToken()
    S.notice("Validated user, email " + t.email())
    User.logIn(t)
    S.redirectTo("/")
  }

}
