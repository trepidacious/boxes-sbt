package boxes.transact.lift.user

import com.mongodb.casbah.Imports._
import java.security.MessageDigest
import net.liftweb.http._
import net.liftweb.util._
import net.liftweb.util.Mailer._
import net.liftweb._
import scala.xml.Elem
import java.net.URLDecoder
import java.net.URLEncoder
import net.liftweb.sitemap.Menu
import net.liftweb.common.Full
import net.liftweb.common.Box.box2Option
import net.liftweb.common.Box.option2Box
import net.liftweb.util.Mailer._
import scala.util.matching.Regex
import boxes.transact.persistence.mongo.MongoNode
import boxes.transact.Txn
import boxes.transact.Box
import boxes.transact.persistence.mongo.MongoMetaNode
import boxes.transact.persistence.mongo.MongoNodeIndex
import boxes.transact.TxnR
import boxes.transact.lift.LiftShelf

class UserBuilder(implicit txn: Txn) {
  def default() = new User(Box(""), Box(""), Box(""), Box(""), Box(None), Box(None), Box(None), Box(false))
}

class User(
  val firstName: Box[String],
  val lastName: Box[String],
  val email: Box[String],
  val initials: Box[String],
  val passHash: Box[Option[String]],
  val validationToken: Box[Option[String]],
  val resetPasswordToken: Box[Option[String]],
  val validated: Box[Boolean]
) extends MongoNode {

  val meta = User
  
//  val emailError = Cal{
//    if (sameEmailExists()) {
//      Some("This email is already taken")
//    } else {
//      None
//    }
//  }
//
//  private val sameEmailExists = Cal{
//    val ourId = Data.mb.keep(User.this)
//    !Data.mb.find[User](MongoDBObject(
//        "email" -> email(),
//        "_id" -> MongoDBObject("$ne" -> ourId)
//        )).toList.isEmpty 
//  }
  
  def checkPass(candidate: String)(implicit txn: TxnR) = passHash().map(Pass.check(candidate, _)).getOrElse(false)
  def checkPass(candidate: Option[String])(implicit txn: TxnR) = (for (h <- passHash(); c <- candidate) yield Pass.check(c, h)).getOrElse(false)
  
  def newValidationToken()(implicit txn: Txn) = {
    val t = User.makeToken
    validationToken.update(Some(t))
    t
  }

  def newResetPasswordToken()(implicit txn: Txn) = {
    val t = User.makeToken
    resetPasswordToken() = Some(t)
    t
  }

  def clearValidationToken()(implicit txn: Txn) {
    validationToken() = None
  }

  def clearResetPasswordToken()(implicit txn: Txn) {
    resetPasswordToken() = None
  }
  
  def id() = LiftShelf.mb.keep2(this).toStringMongod()
  
  def tokenParam(token: Option[String]) = id() + "-" + token.getOrElse("no_token")
  
  def validationURLParam(implicit txn: TxnR) = tokenParam(validationToken())
  def resetPasswordURLParam(implicit txn: TxnR) = tokenParam(resetPasswordToken())
}

object User extends MongoMetaNode {
  
  override val indices = List(MongoNodeIndex("email"))
  
  val tokenLength = 64
  
  private object idLoggedIn extends SessionVar[common.Box[String]](common.Empty) {
    override lazy val __nameSalt = Helpers.nextFuncName
  }
  
  def newUser() = LiftShelf.shelf.transact(implicit txn => new UserBuilder().default())
  
  def findByEmail(email: String) = LiftShelf.mb.findOne[User]("email", email)

  def findById(id: String) = LiftShelf.mb.findById[User](id)
  
  def makeToken = StringHelpers.randomString(User.tokenLength)
  
  def loggedIn: Option[User] = idLoggedIn.is.flatMap(LiftShelf.mb.findById[User](_))
  
  def logOut() {
    //Destroy any extended session
    ExtendedSession.onUserLogout()
    
    idLoggedIn.remove()
    S.session.foreach(_.destroySession())
  }
  
  private def logIn(id: String) { 
    idLoggedIn(Some(id))
    ExtendedSession.onUserLogin(id)
  }

  private def logInAndRedirect(id: String): Nothing = { 
    S.notice(S.?("logged.in"))
    logIn(id)
    S.redirectTo("/")
  }

  private def logInFreshSession(id: String) {
    S.session match {
      case Full(session) => session.destroySessionAndContinueInNewSession(() => logInAndRedirect(id))
      case _ => logInAndRedirect(id)
    }
  }
  
  def logInFreshSession(user: User) {
    logInFreshSession(user.id())
  }
  
  def logIn(user: User) {
    logIn(user.id())
  }

  def logInFromExtendedSession(user: User) {
    idLoggedIn(Some(user.id()))
  }

  private def userAndToken(s: String) = {
    s.split("-").toList match {
      case a::b::nil => Some(a, b)
      case _ => None
    }
  }
  
  def tokenMenu(name: String, userToToken: (User)=>Option[String]) = Menu.param[User](name, name, 
    s => for (ut <- userAndToken(s); 
        user <- LiftShelf.mb.findById[User](ut._1) if Some(ut._2) == userToToken(user)
    ) yield user, 
    user => user.tokenParam(userToToken(user)))
  
  def validationMenu = tokenMenu("User validation", (u: User) => LiftShelf.shelf.read(implicit txn => u.validationToken()))
  def resetPasswordMenu = tokenMenu("User password reset", (u: User) => LiftShelf.shelf.read(implicit txn => u.resetPasswordToken()))

  def urlDecode(in : String) = URLDecoder.decode(in, "UTF-8")
  def urlEncode(in : String) = URLEncoder.encode(in, "UTF-8")
  
  def emailFrom = "noreply@"+S.hostName
  def bccEmail: common.Box[String] = common.Empty

  def resetMailBody(user: User, validationLink: String)(implicit txn: TxnR): Elem = 
    <html>
      <head><title>{S.?("user.reset.email.title")}</title></head>
      <body>
        <p>
          {S.?("dear")} {user.firstName()},<br/><br/>
          {S.?("user.reset.email.instructions")}<br/>
          <a href={validationLink}>{validationLink}</a><br/><br/>
          {S.?("thank.you")}
        </p>
      </body>
    </html>

  def resetMailSubject = S.?("user.reset.email.subject")
  
  def sendResetEmail(hostAndPath: String, user: User)(implicit txn: Txn) {
    val token = user.newResetPasswordToken
    val resetLink = hostAndPath + "/user_reset_password/" + user.resetPasswordURLParam

    val msgXml = resetMailBody(user, resetLink)

    Mailer.sendMail(From(emailFrom),Subject(resetMailSubject),
                    (To(user.email()) :: 
                     generateResetEmailBodies(user, resetLink) :::
                     (bccEmail.toList.map(BCC(_)))): _*)
  }

  def signupMailBody(user: User, validationLink: String)(implicit txn: TxnR): Elem = 
    <html>
      <head><title>{S.?("sign.up.confirmation")}</title></head>
      <body>
        <p>
          {S.?("dear")} {user.firstName()},<br/><br/>
          {S.?("sign.up.validation.link")}<br/>
          <a href={validationLink}>{validationLink}</a><br/><br/>
          {S.?("thank.you")}
        </p>
      </body>
    </html>

  def signupMailSubject = S.?("sign.up.confirmation")
  
  def sendValidationEmail(hostAndPath: String, user: User)(implicit txn: Txn) {
    val token = user.newValidationToken
    val resetLink = hostAndPath + "/user_validate/" + user.validationURLParam

    val msgXml = signupMailBody(user, resetLink)

    Mailer.sendMail(From(emailFrom),Subject(signupMailSubject),
                    (To(user.email()) :: 
                     generateValidationEmailBodies(user, resetLink) :::
                     (bccEmail.toList.map(BCC(_)))): _*)
  }

  /**
   * Generate the mail bodies to send with the validation link.
   * By default, just an HTML mail body is generated by calling signupMailBody
   * but you can send additional or alternative mail by override this method.
   */
  protected def generateValidationEmailBodies(user: User, resetLink: String)(implicit txn: TxnR):
    List[MailBodyType] = List(XHTMLMailBodyType(signupMailBody(user, resetLink)))

  /**
   * Generate the mail bodies to send with the reset link.
   * By default, just an HTML mail body is generated by calling resetMailBody
   * but you can send additional or alternative mail by override this method.
   */
  protected def generateResetEmailBodies(user: User, resetLink: String)(implicit txn: TxnR):
    List[MailBodyType] = List(XHTMLMailBodyType(resetMailBody(user, resetLink)))

  def validatePassword(pass: String) = DefaultPasswordValidation.validate(pass)
//  def validateEmail(email: String) = DefaultEmailValidation.validate(email)
}

object DefaultPasswordValidation {
//  val isAlphabetic = (s: String) => s.filter(c => c.isLetter || c.isWhitespace).length == s.length
//  val isNumeric = (s: String) => s.filter(c => c.isDigit).length == s.length
//  
  val minLength = 8
  val maxLength = 256
//  val minLengthPure = 16
//
  //To avoid telling anyone looking over the user's shoulder that the password is mixed or not, always tell the user all restrictions at once
  def minError = Some(S.?("user.minlength.prefix") + " " + minLength + " " + S.?("user.minlength.suffix"))
  def maxError = Some(S.?("user.maxlength.prefix") + " " + maxLength + " " + S.?("user.maxlength.suffix"))
  
  //FIXME strings as resources
  def validate(s: String) = {
//    val min = if (isAlphabetic(s) || isNumeric(s)) minLengthPure else minLength 
    if (s.length() < minLength) {
      minError
    } else if (s.length() > maxLength) {
      maxError
    } else {
      None
    }
  }
}

//object DefaultEmailValidation {
//  
//  val regex = new Regex("""^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*$""")
//  
//  def validate(s: String) = {
//    //Regex from here: http://www.w3.org/TR/html-markup/datatypes.html#form.data.emailaddress
//    //This is not particularly strict, shouldn't reject any valid email addresses, but may pass invalid ones.
//    //Note that we check the email address properly by trying to mail it, and requiring
//    //the user to visit a link in the email. This is just to remind people gently that they should put something slightly
//    //like an email address in there.
//    if (regex.findFirstMatchIn(s) == None) {
//      Some(S.?("user.invalid.email"))
//    } else {
//      None
//    }
//  }
//}