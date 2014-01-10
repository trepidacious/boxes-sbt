package boxes.lift.user

import boxes.Var
import boxes.lift.box.Data
import boxes.persistence.mongo._
import net.liftweb.common._
import net.liftweb.http.Req
import net.liftweb.http.S
import net.liftweb.http.provider.HTTPCookie
import net.liftweb.util.ConvertableToDate.toMillis
import net.liftweb.util.Helpers.intToTimeSpanBuilder
import net.liftweb.util.Helpers.millis
import net.liftweb.util.StringHelpers

class ExtendedSession() extends MongoNode {
  val meta = ExtendedSession

  /**
   * Long randomly generated string used to look up the session from a cookie (which contains only this cookieId).
   * By being long and random, gives a negligible chance of creating a valid extended session maliciously or accidentally.
   */
  val cookieId = Var(ExtendedSession.makeCookieId)
  val userId = Var("")
  val expirationMillis = Var(millis + ExtendedSession.expirationInterval)
}

object ExtendedSession extends MongoMetaNode with Logger {
  
  override val indices = List(MongoNodeIndex("cookieId"))

  val cookieIdLength = 256
  val expirationInterval = 30.days
  val cookieName = "extsession_id"
  
  def findByCookieId(id: String) = Data.mb.findOne[ExtendedSession]("cookieId", id)
  def makeCookieId = StringHelpers.randomString(ExtendedSession.cookieIdLength)

  def onUserLogin(userObjectId: String) {
    info("ExtendedSession.onUserLogin")
    //Delete any old cookie and/or ExtSession
    onUserLogout()
    
    //Make a new ExtSession for this user, store it in DB
    val extSession = new ExtendedSession()
    extSession.userId() = userObjectId
    Data.mb.keep(extSession)
    
    //Give the browser a cookie with the ExtSession's cookieId to allow retrieving it later for automatic login
    val cookie = HTTPCookie(cookieName, extSession.cookieId())
                    .setMaxAge(((extSession.expirationMillis() - millis) / 1000L).toInt)
                    .setPath("/")
    S.addCookie(cookie)
    S.notice("Added cookie " + cookie.value.getOrElse("blank"))

    info("ExtendedSession.onUserLogin added cookie with id " + cookie.value.openOr("blank"))
  }

  def onUserLogout() {
    for (cookie <- S.findCookie(cookieName)) {
      //Delete cookie from browser
      S.deleteCookie(cookie)
      S.notice("Deleted cookie with id " + cookie.value.openOr("blank"))
      info("Deleted cookie with id " + cookie.value.openOr("blank"))
      //If we have a corresponding ExtSession in database, delete that too
      for {
        cookieId <- cookie.value
        extSession <- findByCookieId(cookieId)
      } {
        Data.mb.forget(extSession)
      }
    }
  }

  /**
   * If there is no User logged in, this checks for a cookie with a cookieId corresponding 
   * to a known ExtendedSession, and if the ExtendedSession references a valid User and 
   * has not expired, that User is logged in using User.logInFreshSession  
   * 
   * To use this, add the following to Boot.scala:
   * <code>
    LiftRules.earlyInStateful.append(ExtendedSession.automaticLoginUsingCookieEarlyInStateful)
   * </code>
   */
  def automaticLoginUsingCookieEarlyInStateful: Box[Req] => Unit = {
    ignoredReq => {
      (User.loggedIn, S.findCookie(cookieName)) match {
        //If no user is logged in, and browser has a cookie, try to log in automatically
        case (None, Full(cookie)) =>
          for (cookieId <- cookie.value) {
            findByCookieId(cookieId) match {
              //Expired session deletes cookie and extSession
              case Some(extSession) if extSession.expirationMillis() < millis => onUserLogout()
              case Some(extSession) => {
                val userId = extSession.userId()
                User.findById(userId) match {
                  //All good - log them in. Note this also triggers onUserLogin, and so will create a fresh cookie/extSession
                  case Some(user) => {
                    S.notice("ExtendedSession about to User.login(" + user + ")")
                    info("ExtendedSession about to User.login(" + user + ")")
                    User.logIn(user)
                  }
                  //Missing user deletes cookie and extSession
                  case None => onUserLogout()
                }
              }
              //Cookie that does not lead to an extSession gets deleted
              case _ => onUserLogout()
            }
          }
        
        //We're already logged in, and/or browser has no cookie
        case _ =>
      }
    }
  }
    
}
