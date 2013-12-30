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
    
    val emailError = Cal{User.validateEmail(u.email())}
    val firstNameError = Cal{if (u.firstName().isEmpty()) Some(S.?("user.first.name.missing")) else None}
    val lastNameError = Cal{if (u.lastName().isEmpty()) Some(S.?("user.last.name.missing")) else None}
    val passError = Cal{User.validatePassword(passA())}
    val passRepeatError = Cal{if (passB() != passA()) Some(S.?("user.reset.passwords.incorrect")) else None}

    def errors = List(emailError, firstNameError, lastNameError, passError, passRepeatError)
    def errorStrings = Cal{errors.flatMap(_())}
      
    def signup() {
      Box.transact{
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
        AjaxTextView(       "Email",                  Path{u.email},      emailError),
        AjaxTextView(       "First Name",             Path{u.firstName},  firstNameError),
        AjaxTextView(       "Last Name",              Path{u.lastName},   lastNameError),
        AjaxPasswordView(   S.?("user.password.a"),   passA,              passError),
        AjaxPasswordView(   S.?("user.password.b"),   passB,              passRepeatError),
        
        AjaxButtonView(   "Sign Up!",     Cal{errorStrings().isEmpty},    signup())
    ))
  }

}
