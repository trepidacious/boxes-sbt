package boxes.transact.lift.user.snippet
import _root_.net.liftweb.util._
import _root_.net.liftweb.common._
import net.liftweb.util.Helpers._
import com.mongodb.casbah.Imports._
import net.liftweb.http.S
import net.liftweb.http.js.JsCommands
import net.liftweb.http.js.JsCmds
import com.mongodb.MongoException
import scalaz._
import Scalaz._
import boxes.transact.lift.comet.InsertCometView
import boxes.transact.lift.user.User
import boxes.transact.lift.comet.AjaxListOfViews
import boxes.transact.lift.comet.AjaxStaticView

class UserSignup() extends InsertCometView[User](User.newUser()){

  val hAndP = S.hostAndPath

  def makeView(u: User) = {
//    
//    //Temporarily store password plaintext, note these are NOT committed to any permanent storage
//    val passA = Var("")
//    val passB = Var("")
//    val complete = Var(false)
//    
//    //Validation - errors for all input fields
//    val emailError = Cal{User.validateEmail(u.email())}
//    val firstNameError = Cal{u.firstName().isEmpty().option(S.?("user.first.name.missing"))}
//    val lastNameError = Cal{u.lastName().isEmpty().option(S.?("user.last.name.missing"))}
//    val passError = Cal{User.validatePassword(passA())}
//    val passRepeatError = Cal{(passB() != passA()).option(S.?("user.reset.passwords.incorrect"))}
//
//    //All errors collapsed to list of strings
//    def errors = List(emailError, firstNameError, lastNameError, passError, passRepeatError)
//    def errorStrings = Cal{errors.flatMap(_())}
//    
//    val redirect = Var(None: Option[String])
//    
//    //Note that we know the method will run only when the button is enabled (and so errorStrings() is empty), and
//    //will run in a transaction
//    def signup() {
//      u.passHash() = Some(PassHash(passA()))
//      try {
//        Data.mb.keep(u)
//        User.sendValidationEmail(hAndP, u); 
//        redirect() = Some("user_signup_complete.html")  
//      } catch {
//        case e: MongoException.DuplicateKey => S.error(S.?("user.email.exists"))
//      }
//    }
//    
    AjaxListOfViews(List(
        AjaxStaticView(<span>hi</span>)
    ))
  }
}
