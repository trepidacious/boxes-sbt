package boxes.transact.lift.user.snippet

import scala.xml.Text
import boxes.BoxImplicits.valueToVal
import boxes.Path
import boxes.list.ListVal
import boxes.Val
import boxes.transact.lift.comet.InsertCometView
import boxes.transact.lift.user.User
import boxes.transact.lift.comet.AjaxListOfViews
import boxes.transact.lift.comet.AjaxDataLinkView
import boxes.transact.lift.comet.AjaxStaticView
import net.liftweb.http.S
import boxes.transact.lift.LiftShelf
import boxes.transact.lift.user.Pass
import boxes.transact.BoxNow
import boxes.transact.lift.comet.AjaxAngularActionView
import boxes.transact.lift.comet.AjaxDataSourceView
import net.liftweb.common.Loggable

case class NewPass(passA: String, passB: String)

class UserEdit() extends InsertCometView[Option[User]](User.loggedIn) with Loggable {
  
  implicit val shelf = LiftShelf.shelf

  val passChanged = BoxNow(false)

  def makeView(ou: Option[User]) = {
    
    def changePass(np: NewPass) {
      if (np.passA != np.passB) {
        S.error(S.?("user.signup.passwords.incorrect"))
      } else User.validatePassword(np.passA) match {
        case Some(error) => S.error(error)
        case None => {
          shelf.transact(implicit txn => {
            ou.foreach(u => {
              u.passHash() = Some(Pass.hash(np.passA))
              passChanged() = true
            })
          })
        }
      }
    }
    
    ou.map(u => {
      AjaxListOfViews(
        AjaxAngularActionView(
            "UserEditCtrl",
            "submitPassGUID", (np: NewPass)=>{
              logger.info("changePass: " + np)
              changePass(np)
            }
          ),
          AjaxDataLinkView("UserEditCtrl", "firstName", u.firstName),
          AjaxDataLinkView("UserEditCtrl", "lastName", u.lastName),
          AjaxDataLinkView("UserEditCtrl", "initials", u.initials),
          AjaxDataSourceView("UserEditCtrl", "passChanged", passChanged)
        )
    }).getOrElse(AjaxStaticView(Text("No user logged in"))) //TODO S.?
  }

}
