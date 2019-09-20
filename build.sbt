name := """kf-search-members"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.0"

val elastic4sVersion = "6.1.4"

libraryDependencies += guice

libraryDependencies ++= Seq(
  "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test,

  "com.sksamuel.elastic4s" %% "elastic4s-core" % elastic4sVersion,

  "com.sksamuel.elastic4s" %% "elastic4s-http" % elastic4sVersion,
  "com.auth0" % "jwks-rsa" % "0.8.3",
  "com.pauldijou" %% "jwt-play" % "4.0.0",
  "com.pauldijou" %% "jwt-core" % "4.0.0"


)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.example.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.example.binders._"
