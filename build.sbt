
//The core project has minimal library dependencies, and provides the basic Box system
lazy val core = project

//New prototype stuff for providing concurrent transactions
lazy val transact = project.dependsOn(core)

//Swing bindings for transact
lazy val transactswing = project.dependsOn(transact, swing)

//JavaFX 8 bindings for transact
lazy val transactfx = project.dependsOn(transact)

//Graph system for transact
lazy val transactgraph = project.dependsOn(transact, swing, graph)

//Swing bindings for boxes
lazy val swing = project.dependsOn(core)

//Swing bindings for graph system for transact
lazy val transactswinggraph = project.dependsOn(transact, transactswing, swing, graph, transactgraph, swinggraph)

//Graph system for boxes - see also swinggraph for Swing bindings of graphs
//TODO need to remove dependency on swing by moving colors and icons to a ui 
//project, and preferably making graph project use non-Swing class instead 
//of Color, and maybe to make a replacement for Image that could just use a 
//String name, with the canvas responsible for converting the name to an 
//image and drawing it.
lazy val graph = project.dependsOn(core, swing)

//Swing bindings for graphs
lazy val swinggraph = project.dependsOn(core, swing, graph)

//Persistence for boxes
lazy val persistence = project.dependsOn(core)

//Persistence for transact boxes
lazy val transactpersistence = project.dependsOn(transact, persistence)

//Demos for all projects except lift and transact
lazy val demo = project.dependsOn(core, swing, graph, swinggraph, persistence)

//Demos for transact projects
lazy val transactdemo = project.dependsOn(transact, transactswing, swing, graph, transactgraph, swinggraph, transactswinggraph)

//Lift bindings
lazy val liftdemo = project.dependsOn(core, graph, persistence)

//Transact Lift bindings
lazy val transactliftdemo = project.dependsOn(core, transact, graph, transactgraph, transactpersistence, persistence)

//Root project just aggregates all subprojects
lazy val root = project.in(file(".")).aggregate(core, transact, swing, graph, swinggraph, persistence, demo, liftdemo)

scalacOptions in ThisBuild  ++= Seq("-unchecked", "-deprecation", "-feature")

version in ThisBuild        := "0.1"

organization in ThisBuild   := "org.boxstack"

scalaVersion in ThisBuild   := "2.10.4"

resolvers ++= Seq(
  "Sonatype OSS Releases"  at "http://oss.sonatype.org/content/repositories/releases/",
  "Sonatype OSS Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"
)
