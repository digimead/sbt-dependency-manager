import sbt.dependency.manager._

DependencyManager

name := "Simple"

version := "1.0.0.0-SNAPSHOT"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-Xcheckinit")

logLevel := Level.Debug

libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.1"
