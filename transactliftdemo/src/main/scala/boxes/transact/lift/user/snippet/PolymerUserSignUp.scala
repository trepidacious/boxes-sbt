package boxes.transact.lift.user.snippet
import _root_.net.liftweb.util._
import _root_.net.liftweb.common._
import net.liftweb.util.Helpers._
import com.mongodb.casbah.Imports._
import net.liftweb.http.S
import net.liftweb.http.js.JsCommands
import net.liftweb.http.js.JsCmds
import com.mongodb.MongoException
import boxes.transact.lift.comet.InsertCometView
import boxes.transact.lift.user.User
import boxes.transact.lift.comet.AjaxListOfViews
import boxes.transact.lift.comet.AjaxStaticView
import boxes.transact.lift.comet.AjaxAngularActionView
import boxes.transact.lift.LiftShelf
import boxes.transact.lift.user.Pass
import scala.util.Try
import boxes.transact.BoxNow
import boxes.transact.lift.comet.AjaxDataSourceView
import boxes.transact.lift.comet.PolymerDataSourceView
import boxes.transact.lift.comet.PolymerActionView

case class PolymerUserCase(firstName: String, lastName: String, email: String, initials: String, passA: String, passB: String)

class PolymerUserSignup() extends InsertCometView[User](User.newUser()) with Loggable {

  val hAndP = S.hostAndPath
  implicit val shelf = LiftShelf.shelf

  def makeView(u: User) = {
    
    val stage = BoxNow(0)

    def signup(uc: PolymerUserCase) {
      if (uc.passA != uc.passB) {
        S.error(S.?("user.signup.passwords.incorrect"))
        
      } else User.validatePassword(uc.passA) match {
        case Some(error) => S.error(error)
        case None => {
          LiftShelf.shelf.transact(implicit txn => {
            u.firstName() = uc.firstName
            u.lastName() = uc.lastName
            u.email() = uc.email
            u.initials() = uc.initials
            u.passHash() = Some(Pass.hash(uc.passA))
          })
          
          try {
            LiftShelf.mb.keep(u)
            LiftShelf.shelf.transact(implicit txn => {
              User.sendValidationEmail(hAndP, u)
              stage() = 1
            })
          } catch {
            case e: MongoException.DuplicateKey => S.error(S.?("user.email.exists"))
          }
        }
      }
    }
    
    AjaxListOfViews(
      PolymerActionView(
        "user-signup",
        "submitGUID", (u: PolymerUserCase)=>{
          logger.info("Submit: " + u)
//          signup(u)
          stage.now() = 1
        }
      ),
      PolymerDataSourceView("user-signup", "stage", stage)
    )
  }
}
