organization := "org.boxstack"

name := "boxes-persistence"

version := "0.1"

scalaVersion := "2.10.2"

scalacOptions ++= Seq("-deprecation", "-feature")

libraryDependencies += "com.novus" %% "salat" % "1.9.3"

libraryDependencies += "com.google.protobuf" % "protobuf-java" % "2.5.0"

libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.2" % "test"
