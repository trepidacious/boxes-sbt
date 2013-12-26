package boxes.lift.user.snippet

import scala.xml.Text
import boxes.BoxImplicits.valueToVal
import boxes.Path
import boxes.lift.user.User
import boxes.lift.comet._
import boxes.lift.comet.AjaxViewImplicits._
import boxes.list.ListVal
import boxes.Val

class UserResetPassword(user: User) extends InsertCometView[User](user){
  def makeView(u: User) = {
    AjaxListOfViews(ListVal(
        AjaxStringView(   "Email",        Path{u.email}),
        AjaxPassView(                     Val(u),     PassReset)
    ))
  }
}

