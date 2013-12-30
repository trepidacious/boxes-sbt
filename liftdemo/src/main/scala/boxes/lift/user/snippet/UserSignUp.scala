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
import boxes.lift.comet.view._
import boxes.Var
import boxes.lift.user.PassHash
import boxes.Box
import net.liftweb.http.js.JsCommands
import net.liftweb.http.js.JsCmds
import com.mongodb.MongoException

class UserSignup() extends InsertCometView[User](new User()){

  val hAndP = S.hostAndPath

  def makeView(u: User) = {
    
    val passA = Var("")
    val passB = Var("")
    
    val requirements = Cal{
      if (u.email().isEmpty()) {
        Some("Please provide an email address (this is required for validation).")
      } else if (u.firstName().isEmpty()) {
        Some("Please provide a first name.")        
      } else if (u.lastName().isEmpty()) {
        Some("Please provide a last name.")        
//      } else if (u.passHash().isEmpty) {
//        Some("Please create a password.")
      //FIXME make this neater, must be a better way of chaining criteria functionally
      } else if (User.validatePassword(passA()).isDefined) {
        User.validatePassword(passA())
      } else if (passA() != passB()) {
        Some(S.?("user.reset.passwords.incorrect"))
      } else {
        None
      }
    }
    
    val errorLabel = Cal{requirements().map(_=>"Errors").getOrElse("")}    
    val errorText = Cal{requirements().getOrElse("")}
    
    def signup() {
      Box.transact{
        requirements() match {
          case Some(r) => S.error(r)
          case None => {
            u.passHash() = Some(PassHash(passA()))
            //TODO use a transaction to ensure that the email is unique before trying to keep, otherwise show an error.
            try {
              Data.mb.keep(u)
              User.sendValidationEmail(hAndP, u); 
              S.notice(S.?("user.created.success"))         
              //FIXME this doesn't work with either location
  //            JsCmds.RedirectTo("index.html")
  //            JsCmds.RedirectTo("/")
            } catch {
              case e: MongoException.DuplicateKey => S.error(S.?("user.email.exists"))
            }
          }
        }
      }
    }
    
    AjaxListOfViews(ListVal(
        AjaxTextView(     "Email",        Path{u.email}),
        AjaxTextView(     "First Name",   Path{u.firstName}),
        AjaxTextView(     "Last Name",    Path{u.lastName}),
//        AjaxPassView(                     Val(u),               PassCreation),
        AjaxPasswordView(     S.?("user.password.a"),    passA),
        AjaxPasswordRepeatView(     S.?("user.password.b"),    passB, passA),
        
        //FIXME integrate this with normal lift error message display somehow? Or add a specific error on each line
//        AjaxStringView(   errorLabel,     errorText),
        AjaxButtonView(   "Sign Up!",     Cal{requirements().isEmpty},    signup())
    ))
  }

}
