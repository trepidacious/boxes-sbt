name := "boxes-transact-lift-demo"

seq(webSettings :_*)

libraryDependencies ++= {
  val liftVersion = "2.5.1"
  Seq(
    "net.liftweb" %% "lift-webkit" % liftVersion % "compile",
    "net.liftweb" %% "lift-mapper" % liftVersion % "compile",
    "org.eclipse.jetty" % "jetty-webapp" % "8.1.7.v20120910" % "container,test",
    "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container,compile" artifacts Artifact("javax.servlet", "jar", "jar"),
   	"org.slf4j" % "slf4j-log4j12" % "1.6.1",
   	"org.scalaz" %% "scalaz-core" % "7.0.5"
 )
}