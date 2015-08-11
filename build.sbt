import xerial.sbt.Pack._

name := "hayato-icfpc2015"

organization := "hayato.io"

version := "0.1"

scalaVersion := "2.11.7"

scalacOptions += "-deprecation"

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "spray" at "http://repo.spray.io/"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.1.2",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  "com.github.scopt" %% "scopt" % "3.3.0",
  "io.spray" %% "spray-json" % "1.3.2",
  "org.scala-lang.modules" %% "scala-swing" % "2.0.0-M2",
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
)

mainClass in (Compile, run) := Some("io.hayato.icfpc2015.HexTetris")

packSettings

packMain := Map("play_icfp2015" -> "io.hayato.icfpc2015.HexTetris")

EclipseKeys.createSrc := EclipseCreateSrc.Default + EclipseCreateSrc.Resource
