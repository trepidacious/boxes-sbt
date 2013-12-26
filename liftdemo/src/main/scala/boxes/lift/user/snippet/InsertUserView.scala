package lifttest.snippet

import boxes.BoxImplicits.valueToVal
import boxes.list.ListVal
import boxes.Cal
import boxes.Path
import boxes.lift.comet._
import _root_.scala.xml.{NodeSeq, Text}
import _root_.net.liftweb.util._
import _root_.net.liftweb.common._
import _root_.java.util.Date
import Helpers._
import net.liftweb.sitemap.Menu
import boxes.lift.user.User
import com.mongodb.casbah.Imports._
import boxes.lift.comet.AjaxViewImplicits._
import net.liftweb.http.S
import boxes.lift.box.Data
import boxes.Val

//object InsertUserView {
//  def newUser(email: String) = {
//    val u = new User()
//    u.email() = email
//    Data.mb.keep(u)
//    u
//  }
//
//  def findOrNew(email: String) = {
//    val u = Data.mb.findOne[User](MongoDBObject("email" -> email))
//    u.getOrElse(newUser(email))
//  }
//  
//  def menu = Menu.param[User]("User", "User", 
//      s => Data.mb.findById[User](s), 
//      user => Data.mb.keep(user).toStringMongod())
//}
//
//class InsertUserView(t: User) extends InsertCometView[User](t){
//
//  def makeView(t: User) = AjaxListOfViews(ListVal(allViews))
//  val hAndP = S.hostAndPath
//
//  private val allViews = List[AjaxView](
//    AjaxTextView(     "Email",        Path{t.email}),
//    AjaxTextView(     "First Name",   Path{t.firstName}),
//    AjaxTextView(     "Last Name",    Path{t.lastName}),
//    AjaxPassView(                     Val(t)),
//    AjaxStringView(   "Validated?",   Path(t.validated)),
//    AjaxButtonView(   "Validate", true, {User.sendValidationEmail(hAndP, t); S.notice("Validation mail sent")})
//  )
//
//}
