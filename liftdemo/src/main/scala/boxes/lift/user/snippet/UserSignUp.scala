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
import scalaz._
import Scalaz._

class UserSignup() extends InsertCometView[User](new User()){

  val hAndP = S.hostAndPath

  def makeView(u: User) = {
    
    //Temporarily store password plaintext, note these are NOT committed to any permanent storage
    val passA = Var("")
    val passB = Var("")
    
    //Validation - errors for all input fields
    val emailError = Cal{User.validateEmail(u.email())}
    val firstNameError = Cal{u.firstName().isEmpty().option(S.?("user.first.name.missing"))}
    val lastNameError = Cal{u.lastName().isEmpty().option(S.?("user.last.name.missing"))}
    val passError = Cal{User.validatePassword(passA())}
    val passRepeatError = Cal{(passB() != passA()).option(S.?("user.reset.passwords.incorrect"))}

    //All errors collapsed to list of strings
    def errors = List(emailError, firstNameError, lastNameError, passError, passRepeatError)
    def errorStrings = Cal{errors.flatMap(_())}
      
    def signup() {
      Box.transact{
        //Make sure we check just before using data, in a transaction to
        //be absolutely sure data is valid at this instant
        errorStrings() match {
          case Nil =>  {
            u.passHash() = Some(PassHash(passA()))
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
          case l => l.foreach(S.error(_))
        }
      }
    }
    
    AjaxListOfViews(ListVal(
        AjaxTextView(     S.?("user.email"),          Path{u.email},      emailError),
        AjaxTextView(     S.?("user.first.name"),     Path{u.firstName},  firstNameError),
        AjaxTextView(     S.?("user.last.name"),      Path{u.lastName},   lastNameError),
        AjaxPasswordView( S.?("user.password.a"),     passA,              passError),
        AjaxPasswordView( S.?("user.password.b"),     passB,              passRepeatError),
        
        AjaxButtonView(   S.?("user.signup.button"),  Cal{errorStrings().isEmpty},    signup())
    ))
  }

}
