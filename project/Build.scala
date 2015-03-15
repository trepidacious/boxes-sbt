// See here for a good example of a similar project:
// http://www.scala-sbt.org/0.13/docs/Full-Def-Example.html

import sbt._
import Keys._

object Libs {
  val scalatest     = "org.scalatest" %% "scalatest" % "2.2.4" % "test"
  val shapeless     = "com.chuusai" % "shapeless_2.10.4" % "2.0.0"
  val grizzled      = "org.clapper" %% "grizzled-slf4j" % "1.0.2"

  val salat         = "com.novus" %% "salat" % "1.9.9"
  val protobuf      = "com.google.protobuf" % "protobuf-java" % "2.5.0"

  val scalafx       = "org.scalafx" %% "scalafx" % "8.0.0-R4"

  val vertxScala    = "io.vertx" % "lang-scala" % "1.0.0"
  val vertxPlatform = "io.vertx" % "vertx-platform" % "2.1.5"

  val liftVersion = "2.6"
  val allLift = Seq(
    "net.liftweb"       %% "lift-webkit"        % liftVersion        % "compile",
    "net.liftweb"       %% "lift-mapper"        % liftVersion        % "compile",
    "net.liftmodules"   %% "lift-jquery-module_2.6" % "2.8",
    "org.eclipse.jetty" % "jetty-webapp"        % "8.1.7.v20120910"  % "container,test",
    "org.eclipse.jetty" % "jetty-plus"          % "8.1.7.v20120910"  % "container,test", // For Jetty Config
    "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container,test" artifacts Artifact("javax.servlet", "jar", "jar"),
    "ch.qos.logback"    % "logback-classic"     % "1.0.6",
    "org.specs2"        %% "specs2"             % "2.3.12"           % "test",
    "com.h2database"    % "h2"                  % "1.3.167",
    "org.scalaz" %% "scalaz-core" % "7.1.1"
  )

}

object Build extends Build {

  import Libs._

  // Default settings for all sub projects
  val buildSettings = Seq (
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature"),
    version := "0.1",
    organization := "org.rebeam",
    scalaVersion := "2.11.5",
    libraryDependencies += scalatest,
    publishTo := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository")))
  )

  def subProject(name: String) = Project(name, file(name), settings = buildSettings)

  //The core project has minimal library dependencies, and provides the basic Box system
  lazy val core = subProject ("core")

  //New prototype stuff for providing concurrent transactions
  val transactLibs = Seq(shapeless, grizzled)
  lazy val transact = subProject("transact")
    .settings(libraryDependencies ++= transactLibs)
    .dependsOn(core)

  //Swing bindings for transact
  lazy val transactswing = subProject("transactswing")
    .dependsOn(transact, swing)

  //JavaFX 8 bindings for transact
  val transactfxLibs = Seq(scalafx)
  lazy val transactfx = subProject("transactfx")
    .settings(libraryDependencies ++= transactfxLibs)
    .dependsOn(transact)

  //Graph system for transact
  lazy val transactgraph = subProject("transactgraph")
    .dependsOn(transact, swing, graph)

  //Swing bindings for boxes
  lazy val swing = subProject("swing")
    .dependsOn(core)

  //Swing bindings for graph system for transact
  lazy val transactswinggraph = subProject("transactswinggraph")
    .dependsOn(transact, transactswing, swing, graph, transactgraph, swinggraph)

  //Graph system for boxes - see also swinggraph for Swing bindings of graphs
  //TODO need to remove dependency on swing by moving colors and icons to a ui
  //project, and preferably making graph project use non-Swing class instead
  //of Color, and maybe to make a replacement for Image that could just use a
  //String name, with the canvas responsible for converting the name to an
  //image and drawing it.
  lazy val graph = subProject("graph")
    .dependsOn(core, swing)

  //Swing bindings for graphs
  lazy val swinggraph = subProject("swinggraph")
    .dependsOn(core, swing, graph)

  //Persistence for boxes
  val persistenceLibs = Seq(salat, protobuf)
  lazy val persistence = subProject("persistence")
    .settings(libraryDependencies ++= persistenceLibs)
    .dependsOn(core)

  //Persistence for transact boxes
  val transactpersistenceLibs = persistenceLibs
  lazy val transactpersistence = subProject("transactpersistence")
    .settings(libraryDependencies ++= transactpersistenceLibs)
    .dependsOn(transact, persistence)

  //Demos for all projects except lift and transact
  lazy val demo = subProject("demo")
    .dependsOn(core, swing, graph, swinggraph, persistence)

  //Demos for transact projects
  lazy val transactdemo = subProject("transactdemo")
    .dependsOn(transact, transactswing, swing, graph, transactgraph, swinggraph, transactswinggraph)

  //TODO move the webSettings stuff here from liftdemo build.sbt, possibly move to 1.0.0 version of https://github.com/earldouglas/xsbt-web-plugin
  //Lift bindings
  lazy val liftdemo = subProject("liftdemo")
    .settings(libraryDependencies ++= allLift) //++ Seq(WebPlugin.webSettings :_*)
    .dependsOn(core, graph, persistence)

  //TODO move the webSettings stuff here from transactliftdemo build.sbt, possibly move to 1.0.0 version of https://github.com/earldouglas/xsbt-web-plugin
  //Transact Lift bindings
  lazy val transactliftdemo = subProject("transactliftdemo")
    .settings(libraryDependencies ++= allLift) //++ Seq(WebPlugin.webSettings :_*)
    .dependsOn(core, transact, graph, transactgraph, transactpersistence, persistence)

  def replaceExtension(s: String, newExtension: String) =
    (if (s.contains('.')) s.take(s.lastIndexOf(".")) else s) + newExtension

  //Vert.x stuff
  lazy val zipmod = taskKey[File]("Makes a zip of vertx module.")
  lazy val modsTarget = settingKey[File]("The target directory in which to build module.")

  val vertxLibs = Seq(vertxPlatform, vertxScala)

  lazy val vertx = subProject("vertx")
    .settings(
      watchSources += baseDirectory.value / "mod.json",
      libraryDependencies ++= vertxLibs,
      modsTarget := target.value / "mods",
      zipmod := {
        //Get the jar File produced by package task
        //Note that "package" task conflicts with keyword, hence backticks.
        val vertxJar = (Keys.`package` in Compile).value

        val dir = baseDirectory.value
        val modsDir = modsTarget.value
        val v = version.value
        val n = name.value
        val sbv = scalaBinaryVersion.value
        val o = organization.value
        val modDir = modsDir / (o + "~" + n + "_" + sbv + "~" + v)
        val modLibDir = modDir / "lib"
        val targetDir = target.value
        println("Creating module in " + modDir + "...")
        IO.createDirectory(modDir)
        IO.createDirectory(modLibDir)
        IO.copyFile(vertxJar, modLibDir / vertxJar.getName(), true)
        IO.copyDirectory(dir / "src/main/resources", modDir, true)
        val zipContents = Path.allSubpaths(modDir)
        val zipFile = targetDir / replaceExtension(vertxJar.getName(), ".zip")
        println("Zipping module to " + zipFile + "...")
        IO.zip(zipContents, zipFile)
        zipFile
      }
    )
    .dependsOn(core, transact, graph, transactgraph, transactpersistence, persistence)

  lazy val root = Project (
    "root",
    file("."),
    settings = buildSettings
  ) aggregate (
    core,
    transact,
    transactswing,
    transactfx,
    transactgraph,
    swing,
    transactswinggraph,
    graph,
    swinggraph,
    persistence,
    transactpersistence,
    demo,
    transactdemo,
    liftdemo,
    transactliftdemo,
    vertx
  )

}
