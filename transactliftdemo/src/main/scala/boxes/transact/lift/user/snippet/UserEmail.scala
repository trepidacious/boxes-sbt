package boxes.transact.lift.user.snippet
import scala.xml.NodeSeq
import _root_.net.liftweb.util._
import _root_.net.liftweb.common._
import net.liftweb.util.Helpers._
import net.liftweb.http.StatefulSnippet
import boxes.transact.lift.user.User
import boxes.transact.lift.LiftShelf

//TODO doesn't need to be stateful?
class UserEmail extends StatefulSnippet {
  
  implicit val shelf = LiftShelf.shelf
  
  var dispatch: DispatchIt = {
    case "widget" => widget _
  }

  def widget(xhtml: NodeSeq): NodeSeq = {
    
    //TODO can just return the css selector?
    (User.loggedIn match {
      
      case Some(user) => "*" #>
        <li class="dropdown">
          <a class="dropdown-toggle" href="#" data-toggle="dropdown">
            {user.email.now()} <b class="caret"></b>
          </a>
          <ul class="dropdown-menu">
            <li><a tabindex="-1" href="/user_edit">Edit details</a></li>

            <li class="divider"></li>

            <li><a tabindex="-1" href="/user_logout">Logout</a></li>
          </ul>               
        </li>
            
      case _ => "*" #> <li><a href="/user_signup">Sign up</a></li><li><a href="/user_login">Login</a></li>
      
    }).apply(xhtml)
  }
  
}

