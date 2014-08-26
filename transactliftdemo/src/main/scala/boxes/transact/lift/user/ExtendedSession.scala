package boxes.transact.lift.user

import boxes.Var
import net.liftweb.common._
import net.liftweb._
import net.liftweb.http.Req
import net.liftweb.http.S
import net.liftweb.http.provider.HTTPCookie
import net.liftweb.util.ConvertableToDate.toMillis
import net.liftweb.util.Helpers.intToTimeSpanBuilder
import net.liftweb.util.Helpers.millis
import net.liftweb.util.StringHelpers
import net.liftweb.http.SessionVar
import net.liftweb.util.Helpers
import net.liftweb.http.LiftRules
import net.liftweb.http.LiftSession
import boxes.transact.persistence.mongo.MongoNode
import boxes.transact.Txn
import boxes.transact.persistence.mongo.MongoMetaNode
import boxes.transact.persistence.mongo.MongoNodeIndex
import boxes.transact.lift.LiftShelf

class ExtendedSessionBuilder(implicit txn: Txn) {
  def default() = new ExtendedSession(boxes.transact.Box(ExtendedSession.makeCookieId), boxes.transact.Box(""), boxes.transact.Box(millis + ExtendedSession.expirationInterval))
}

class ExtendedSession(val cookieId : boxes.transact.Box[String], val userId: boxes.transact.Box[String], val expirationMillis: boxes.transact.Box[Long]) extends MongoNode {
  val meta = ExtendedSession
  
  override def toString() = "ExtendedSession(" + cookieId + ", " + userId + ", " + expirationMillis + ")"
}

object ExtendedSession extends MongoMetaNode with Loggable {  
  override val indices = List(MongoNodeIndex("cookieId"))

  val cookieIdLength = 256
  val expirationInterval = 30.days
  val cookieName = "extsession_id"

  //This is set to true when session has been setup, this works around the
  //issue that Lift calls earlyInStateful twice when a new session has to be
  //created. We can ignore the first call, then onSetupSession should be called
  //so that on the next call of earlyInStateful we can run normally.
  private object sessionIsSetup extends SessionVar[Boolean](false) {
    override lazy val __nameSalt = Helpers.nextFuncName
  }

  def isSessionSetup() = sessionIsSetup.get
  
  def boot() {
    LiftRules.earlyInStateful.append(automaticLoginUsingCookieEarlyInStateful)
    LiftSession.onSetupSession ::= { _: LiftSession => sessionIsSetup(true) }
  }
  
  def findByCookieId(id: String) = LiftShelf.mb.findOne[ExtendedSession]("cookieId", id)
  def makeCookieId = StringHelpers.randomString(ExtendedSession.cookieIdLength)

  def onUserLogin(userObjectId: String) {
    deleteSessionAndCookie()    
    createSessionAndCookie(userObjectId)

  }

  def onUserLogout() = deleteSessionAndCookie()
  
  def deleteSessionAndCookie() {
    for (cookie <- S.findCookie(cookieName)) {
      //Delete cookie from browser
      S.deleteCookie(cookie)
      //If we have a corresponding ExtSession in database, delete that too
      for {
        cookieId <- cookie.value
        extSession <- findByCookieId(cookieId)
      } {
        LiftShelf.mb.forget(extSession)
      }
    }
  }
  
  def createSessionAndCookie(userObjectId: String) {
    //Make a new ExtSession for this user, store it in DB
    val (cookie, extSession) = LiftShelf.shelf.transact(implicit txn => {
      val extSessionBuilder = new ExtendedSessionBuilder
      val extSession = extSessionBuilder.default
      extSession.userId() = userObjectId

      (HTTPCookie(cookieName, extSession.cookieId())
        .setMaxAge(((extSession.expirationMillis() - millis) / 1000L).toInt)
        .setPath("/"), extSession)
    })

    LiftShelf.mb.keep(extSession)
    
    //Give the browser a cookie with the ExtSession's cookieId to allow retrieving it later for automatic login
    S.addCookie(cookie)
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
      //Don't run until the session is properly setup, so we don't run on first earlyInStateful where cookies are not retained, etc.
      if (isSessionSetup) {
        //If we are logged out, try to log in with cookie
        for (cookie <- S.findCookie(cookieName) if User.loggedIn.isEmpty) {
          //Delete cookie from browser, whether we log in with it or not, its job is done
          S.deleteCookie(cookieName)
          
          for (cookieId <- cookie.value; extSession <- findByCookieId(cookieId)) {
            //Forget the extSession - whether we use it or not, its job is done
            LiftShelf.mb.forget(extSession)

            val (expiration, userId) = LiftShelf.shelf.read(implicit txn => (extSession.expirationMillis(), extSession.userId()))
            
            //If extSession is not expired, allow login
            if (expiration > millis) {
              for (user <- User.findById(userId)) {
                
                //So they can log in next time
                createSessionAndCookie(userId)

                //Log them in
                User.logInFromExtendedSession(user)
              }
            }
          }
        }
      }
    }
  }
    
}
