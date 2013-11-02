package lifttest {
package snippet {

import _root_.scala.xml.{NodeSeq, Text}
import _root_.net.liftweb.util._
import _root_.net.liftweb.common._
import _root_.java.util.Date
import Helpers._
import net.liftweb.http.StatefulSnippet
import boxes.lift.user.User

//TODO doesn't need to be stateful?
class ShowUserEmail extends StatefulSnippet {
  
  var dispatch: DispatchIt = {
    case "widget" => widget _
  }

  def widget(xhtml: NodeSeq): NodeSeq = {
    
    //TODO can just return the css selector?
    (User.loggedIn match {
      
      case Some(user) => "*" #>
        <li class="dropdown">
          <a class="dropdown-toggle" href="#" data-toggle="dropdown">
            {user.email()}
          </a>
          <ul class="dropdown-menu">
            <li><a tabindex="-1" href="/user_edit">Edit Details</a></li>

            <!--<li><a tabindex="-1" href="/user_change_password">Change Password</a></li>-->

            <li class="divider"></li>

            <li><a tabindex="-1" href="/user_logout">Logout</a></li>
          </ul>               
        </li>
            
      case _ => "*" #> <li><a href="/user_signup">Sign Up</a></li><li><a href="/user_login">Login</a></li>
      
    }).apply(xhtml)
  }
  
}

}
}
