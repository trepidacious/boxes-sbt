package boxes.lift.user

import com.mongodb.casbah.Imports._
import java.security.MessageDigest
import boxes._
import net.liftweb.http._
import net.liftweb.util._
import net.liftweb.util.Mailer._
import net.liftweb._
import scala.xml.Elem
import java.net.URLDecoder
import java.net.URLEncoder
import net.liftweb.sitemap.Menu
import boxes.persistence.mongo.MongoMetaNode
import boxes.persistence.mongo.MongoNodeIndex
import boxes.persistence.mongo.MongoNode
import net.liftweb.common.Full
import boxes.lift.box.Data
import net.liftweb.common.Box.box2Option
import net.liftweb.common.Box.option2Box
import net.liftweb.util.Mailer._
import scala.util.matching.Regex

object PassHash {
  val saltLength = 64
  
  def apply(pass: String): PassHash = {
    val salt = StringHelpers.randomString(saltLength)
    val hash = PassHash.hash(pass, salt)
    PassHash(salt, hash)
  }
  
  def hash(pass: String, salt: String) = 
    SecurityHelpers.base64Encode(MessageDigest.getInstance("SHA-512").digest((pass + "-" + salt).getBytes("UTF-8")))
}

case class PassHash(salt: String, hash: String) {
  def checkPass(pass: String) = PassHash.hash(pass, salt) == hash
}

class User extends MongoNode {
  val meta = User
  
  val firstName = Var("")
  val lastName = Var("")
  val email = Var("")
  val initials = Var("")
  val passHash: Var[Option[PassHash]] = Var(None)
  val token: Var[Option[String]] = Var(None)
  val validated: Var[Boolean] = Var(false)
  
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
  
  def checkPass(pass: String) = passHash().map(_.checkPass(pass)).getOrElse(false)
  def checkPass(pass: Option[String]) = (for (ph <- passHash(); p <- pass) yield ph.checkPass(p)).getOrElse(false)
  
  def newToken() = {
    val t = User.makeToken
    token() = Some(t)
    t
  }
  
  def validationParameter() = Data.mb.keep(this).toStringMongod() + "-" + token().getOrElse("no_token")
  
}

object User extends MongoMetaNode {
  
  override val indices = List(MongoNodeIndex("email"))
  
  val tokenLength = 64
  
  private object idLoggedIn extends SessionVar[common.Box[String]](common.Empty) {
    override lazy val __nameSalt = Helpers.nextFuncName
  }
  
  def findByEmail(email: String) = Data.mb.findOne[User]("email", email)
  
  def makeToken = StringHelpers.randomString(User.tokenLength)
  
  def loggedIn: Option[User] = idLoggedIn.is.flatMap(Data.mb.findById[User](_))
  
  def logOut() {
    idLoggedIn.remove()
    S.session.foreach(_.destroySession())
  }
  
  def logIn(id: String) { 
    idLoggedIn(Some(id))
  }

  def logInAndRedirect(id: String): Nothing = { 
    S.notice(S.?("logged.in"))
    idLoggedIn(Some(id))
    S.redirectTo("/")
  }

  def logInFreshSession(id: String) {
    S.session match {
      case Full(session) => session.destroySessionAndContinueInNewSession(() => logInAndRedirect(id))
      case _ => logInAndRedirect(id)
    }
  }

  def logInFreshSession(user: User) {
    logInFreshSession(Data.mb.keep(user).toStringMongod())
  }

  def logIn(user: User) {
    logIn(Data.mb.keep(user).toStringMongod())
  }
  
  private def userAndToken(s: String) = {
    s.split("-").toList match {
      case a::b::nil => Some(a, b)
      case _ => None
    }
  }
  
  def validateMenu(name: String) = Menu.param[User](name, name, 
    s => for (ut <- userAndToken(s); 
        user <- Data.mb.findById[User](ut._1) if Some(ut._2) == user.token()
    ) yield user, 
    user => Data.mb.keep(user).toStringMongod() + "-" + user.token().getOrElse("NO_TOKEN"))
  
  def urlDecode(in : String) = URLDecoder.decode(in, "UTF-8")
  def urlEncode(in : String) = URLEncoder.encode(in, "UTF-8")
  
  def emailFrom = "noreply@"+S.hostName
  def bccEmail: common.Box[String] = common.Empty

  def resetMailBody(user: User, validationLink: String): Elem = 
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
  
  def sendResetEmail(hostAndPath: String, user: User) {
    val token = user.newToken()
    val resetLink = hostAndPath + "/user_reset_password/" + user.validationParameter()

    val msgXml = resetMailBody(user, resetLink)

    Mailer.sendMail(From(emailFrom),Subject(resetMailSubject),
                    (To(user.email()) :: 
                     generateResetEmailBodies(user, resetLink) :::
                     (bccEmail.toList.map(BCC(_)))): _*)
  }

  def signupMailBody(user: User, validationLink: String): Elem = 
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
  
  def sendValidationEmail(hostAndPath: String, user: User) {
    val token = user.newToken()
    val resetLink = hostAndPath + "/user_validate/" + user.validationParameter()

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
  protected def generateValidationEmailBodies(user: User, resetLink: String):
    List[MailBodyType] = List(XHTMLMailBodyType(signupMailBody(user, resetLink)))

  /**
   * Generate the mail bodies to send with the reset link.
   * By default, just an HTML mail body is generated by calling resetMailBody
   * but you can send additional or alternative mail by override this method.
   */
  protected def generateResetEmailBodies(user: User, resetLink: String):
    List[MailBodyType] = List(XHTMLMailBodyType(resetMailBody(user, resetLink)))

}