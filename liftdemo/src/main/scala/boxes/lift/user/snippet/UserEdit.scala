package boxes.lift.user.snippet

import scala.xml.Text
import boxes.BoxImplicits.valueToVal
import boxes.Path
import boxes.lift.user.User
import boxes.lift.comet._
import boxes.lift.comet.AjaxViewImplicits._
import boxes.list.ListVal

class UserEdit() extends InsertCometView[Option[User]](User.loggedIn){

  def makeView(ou: Option[User]) = {
    ou.map(u => AjaxListOfViews(ListVal(
        AjaxStringView(     "Email",        Path{u.email}),
        AjaxTextView(     "First Name",   Path{u.firstName}),
        AjaxTextView(     "Last Name",    Path{u.lastName}),
        AjaxPassView(                     Path(u.passHash))
    ))).getOrElse(AjaxNodeSeqView(control = Text("No user logged in"))) //TODO S.?
  }

}
