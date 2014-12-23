name := "boxes-vertx"

// Fork required to avoid conflicts when compiling the .scala source on the fly
// fork := true

libraryDependencies ++= Seq(
  // If changing lang-scala version, make sure src/main/resources/langs.properties is updated too
  "io.vertx" % "lang-scala" % "0.3.+" changing(),
  "io.vertx" % "vertx-platform" % "2.1.5"
)

// resolvers += "Sonatype Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

//resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"
