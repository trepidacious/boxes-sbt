package bootstrap.liftweb

import net.liftweb.http.{Html5Properties, LiftRules, Req}
import net.liftweb.sitemap.{Menu, SiteMap}
import net.liftweb.util.Mailer
import net.liftweb.common._
import javax.mail.Authenticator
import javax.mail.PasswordAuthentication
import net.liftweb.sitemap._
import net.liftweb.sitemap.Loc._
import net.liftweb.http._
import boxes.transact.lift.user.User
import boxes.transact.lift.user.ExtendedSession

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
    LiftRules.addToPackages("boxes.transact.lift.demo")
    LiftRules.addToPackages("boxes.transact.lift.user")
    LiftRules.addToPackages("boxes.transact.lift")

    configMailer("smtp.gmail.com", System.getProperty("gmailUser"), System.getProperty("gmailPassword"))

    val loggedIn = If(() => User.loggedIn.isDefined,
                      () => RedirectResponse("/user_login"))

    // Build SiteMap
    def sitemap = SiteMap(
      Menu.i("Home") / "index"  >> Hidden,
      Menu.i("Angular") / "angular",
      Menu.i("Polymer") / "polymer-demo",

      //User management pages
      Menu.i("User signup") / "user_signup" >> Hidden,
      Menu.i("User signup (Polymer)") / "polymer-user-signup" >> Hidden,
      Menu.i("User login") / "user_login" >> Hidden,
      Menu.i("User logout") / "user_logout" >> Hidden >> loggedIn,
      Menu.i("User edit") / "user_edit" >> Hidden >> loggedIn,
      User.validationMenu / "user_validate" >> Hidden,
      User.resetPasswordMenu / "user_reset_password" >> Hidden,
      //      Menu.i("User signup complete") / "user_signup_complete"  >> Hidden,
      
//      Menu.i("Timesheet") / "timesheet_view" >> loggedIn,

      //Anything we get via bower
      Menu.i("Bower Components") / "bower_components" / ** >> Hidden,

      //Polymer elements
      Menu.i("Polymer Demo Elements") / "polymer-demo-elements" / ** >> Hidden
    )
    
    LiftRules.setSiteMapFunc(() => sitemap)

//    // Show the spinny image when an Ajax call starts
//    LiftRules.ajaxStart =
//      Full(() => LiftRules.jsArtifacts.show("ajax-loader").cmd)
//
//    // Make the spinny image go away when it ends
//    LiftRules.ajaxEnd =
//      Full(() => LiftRules.jsArtifacts.hide("ajax-loader").cmd)

    //FIXME what is this meant to do?
//    LiftRules.loggedInTest = Full(() => User.loggedIn_?)
      
    //Allow users to log in using cookies
    ExtendedSession.boot()
    
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