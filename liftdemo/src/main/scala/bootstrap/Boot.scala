package bootstrap.liftweb

import net.liftweb.http.{Html5Properties, LiftRules, Req}
import net.liftweb.sitemap.{Menu, SiteMap}
import net.liftweb.util.Mailer
import net.liftweb.common._
import javax.mail.Authenticator
import javax.mail.PasswordAuthentication
import boxes.lift.demo.snippet.InsertFrameView
import boxes.lift.demo.snippet.FrameEdit

/**
 * A class that's instantiated early and run.  It allows the application
 * to modify lift's environment
 */
class Boot {
  def boot {
    
    // where to search snippet
    LiftRules.addToPackages("code")
    LiftRules.addToPackages("boxes.lift.demo")
    LiftRules.addToPackages("boxes.lift.user")
    LiftRules.addToPackages("boxes.lift")

    configMailer("smtp.gmail.com", System.getProperty("gmailUser"), System.getProperty("gmailPassword"))

    //FIXME reenable?
//    TableSorter.init

    // Build SiteMap
    def sitemap = SiteMap(
//      Menu.i("Home") / "index",
//      Menu.i("Comet Test") / "comet-test", // >> loggedIn
//      InsertFrameView.menu / "frame", //>> Hidden >> loggedIn,
      FrameEdit.menu / "frame_edit", //>> Hidden >> loggedIn,
//      Menu.param[String]("Param", "Param", 
//                                   s => Full(s), 
//                                   s => s) / "param"
      Menu.i("Create Frame") / "frame_create" // >> loggedIn
    )
    LiftRules.setSiteMapFunc(() => sitemap)

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