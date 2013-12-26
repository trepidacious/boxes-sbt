package boxes.lift.user.snippet
import boxes.list.ListVal
import boxes.Cal
import boxes.Path
import boxes.lift.comet._
import _root_.net.liftweb.util._
import _root_.net.liftweb.common._
import net.liftweb.util.Helpers._
import com.mongodb.casbah.Imports._
import net.liftweb.http.S
import boxes.lift.user.User
import boxes.lift.comet._
import boxes.lift.comet.AjaxViewImplicits._
import boxes.lift.box.Data
import boxes.lift.user.User
import boxes.Val

class UserSignup() extends InsertCometView[User](new User()){

  val hAndP = S.hostAndPath

  def makeView(u: User) = {
    
    val requirements = Cal{
      if (u.email().isEmpty()) {
        Some("Please provide an email address (this is required for validation).")
      } else if (u.firstName().isEmpty()) {
        Some("Please provide a first name.")        
      } else if (u.lastName().isEmpty()) {
        Some("Please provide a last name.")        
      } else if (u.passHash().isEmpty) {
        Some("Please create a password.")
      } else {
        None
      }
    }
    
    val errorLabel = Cal{requirements().map(_=>"Errors").getOrElse("")}    
    val errorText = Cal{requirements().getOrElse("")}
    
    def signup() {
      //TODO use a transaction to ensure that the email is unique before trying to keep, otherwise show an error.
      Data.mb.keep(u)
      User.sendValidationEmail(hAndP, u); 
      S.notice("Validation mail sent")
    }
    
    AjaxListOfViews(ListVal(
        AjaxTextView(     "Email",        Path{u.email}),
        AjaxTextView(     "First Name",   Path{u.firstName}),
        AjaxTextView(     "Last Name",    Path{u.lastName}),
        AjaxPassView(                     Val(u),               PassCreation),
        
        //FIXME integrate this with normal lift error message display somehow? Or add a specific error on each line
        AjaxStringView(   errorLabel,     errorText),
        AjaxButtonView(   "Sign Up!",     Cal{requirements().isEmpty},    signup())
    ))
  }

}
