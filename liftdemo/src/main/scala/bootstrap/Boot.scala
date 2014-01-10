package bootstrap.liftweb

import net.liftweb.http.{Html5Properties, LiftRules, Req}
import net.liftweb.sitemap.{Menu, SiteMap}
import net.liftweb.util.Mailer
import net.liftweb.common._
import javax.mail.Authenticator
import javax.mail.PasswordAuthentication
import boxes.lift.demo.snippet.FrameEdit
import net.liftweb.sitemap._
import net.liftweb.sitemap.Loc._
import net.liftweb.http._
import boxes.lift.user.User
import boxes.lift.user.snippet.UserValidate
import boxes.lift.user.ExtendedSession

/**
 * A class that's instantiated early and run.  It allows the application
 * to modify lift's environment
 */
class Boot {
  def boot {

    //FIXME change to logging configured from file
    Logger.setup = Full(Log4j.withDefault)
    
    //Where to get resources
    LiftRules.resourceNames = "boxes" :: "timesheet" :: Nil
    
    // where to search snippet
    LiftRules.addToPackages("boxes.lift.demo")
    LiftRules.addToPackages("boxes.lift.user")
    LiftRules.addToPackages("boxes.lift")

    configMailer("smtp.gmail.com", System.getProperty("gmailUser"), System.getProperty("gmailPassword"))

    val loggedIn = If(() => boxes.lift.user.User.loggedIn.isDefined,
                      () => RedirectResponse("/user_login"))

    
    //FIXME reenable?
//    TableSorter.init

    // Build SiteMap
    def sitemap = SiteMap(
      Menu.i("Home") / "index"  >> Hidden,
      Menu.i("User signup complete") / "user_signup_complete"  >> Hidden,
      FrameEdit.menu / "frame_edit" >> Hidden,// >> loggedIn,
      Menu.i("Create frame") / "frame_create", // >> loggedIn
      Menu.i("User signup") / "user_signup" >> Hidden,
      Menu.i("Login box") / "user_login" >> Hidden,
      Menu.i("Logout box") / "user_logout" >> Hidden >> loggedIn,
      Menu.i("User edit") / "user_edit" >> Hidden >> loggedIn,
      Menu.i("Timesheet") / "timesheet_view" >> loggedIn,
      
      User.validationMenu / "user_validate" >> Hidden,
      User.resetPasswordMenu / "user_reset_password" >> Hidden
    )
    
    LiftRules.setSiteMapFunc(() => sitemap)

    // Show the spinny image when an Ajax call starts
    LiftRules.ajaxStart =
      Full(() => LiftRules.jsArtifacts.show("ajax-loader").cmd)

    // Make the spinny image go away when it ends
    LiftRules.ajaxEnd =
      Full(() => LiftRules.jsArtifacts.hide("ajax-loader").cmd)

    //FIXME what is this meant to do?
//    LiftRules.loggedInTest = Full(() => User.loggedIn_?)
      
    //Allow users to log in using cookies
    LiftRules.earlyInStateful.append(ExtendedSession.automaticLoginUsingCookieEarlyInStateful)
      
    // Force the request to be UTF-8
    LiftRules.early.append(_.setCharacterEncoding("UTF-8"))

    // Use HTML5 for rendering
    LiftRules.htmlProperties.default.set((r: Req) =>
      new Html5Properties(r.userAgent))
  }
  
  
  def configMailer(host: String, user: String, password: String) {
    // Enable TLS support
    System.setProperty("mail.smtp.starttls.enable","true");
    // Set the host name
    System.setProperty("mail.smtp.host", host) // Enable authentication
    System.setProperty("mail.smtp.auth", "true") // Provide a means for authentication. Pass it a Can, which can either be Full or Empty
    Mailer.authenticator = Full(new Authenticator {
      override def getPasswordAuthentication = new PasswordAuthentication(user, password)
    })
  }

}