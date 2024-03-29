name := """kf-search-members"""
organization := "io.kidsfirst"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.10"

val keycloakVersion = "4.0.0.Final"
val elastic4sVersion = "7.3.1"

libraryDependencies += guice
libraryDependencies += ws

libraryDependencies ++= Seq(
  "org.scalactic" %% "scalactic" % "3.0.8",
  "org.scalatest" %% "scalatest" % "3.0.8" % "test",

  "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % Test,

  "com.sksamuel.elastic4s" %% "elastic4s-core" % elastic4sVersion
    exclude("commons-logging", "commons-logging")
    exclude("org.apache.logging.log4j", "log4j-slf4j-impl"),

  "com.sksamuel.elastic4s" %% "elastic4s-client-esjava" % elastic4sVersion
    exclude("commons-logging", "commons-logging")
    exclude("org.apache.logging.log4j", "log4j-slf4j-impl"),

  "com.sksamuel.elastic4s" %% "elastic4s-testkit" % elastic4sVersion % Test
    exclude("commons-logging", "commons-logging")
    exclude("org.apache.logging.log4j", "log4j-slf4j-impl"),

  "org.keycloak"      % "keycloak-core"         % keycloakVersion,

  "com.auth0" % "jwks-rsa" % "0.8.3",
  "com.pauldijou" %% "jwt-play" % "4.0.0",
  "com.pauldijou" %% "jwt-core" % "4.0.0",
  "org.mockito" % "mockito-all" % "1.10.19" % Test
)

mainClass in assembly := Some("play.core.server.ProdServerStart")
fullClasspath in assembly += Attributed.blank(PlayKeys.playPackageAssets.value)

assemblyMergeStrategy in assembly := {
  case manifest if manifest.contains("MANIFEST.MF") =>
    // We don't need manifest files since sbt-assembly will create
    // one with the given settings
    MergeStrategy.discard
  case referenceOverrides if referenceOverrides.contains("reference-overrides.conf") =>
    // Keep the content for all reference-overrides.conf files
    MergeStrategy.concat
  case PathList("javax", "activation", xs @ _*) => MergeStrategy.first
  case x =>
    // For all the other files, use the default sbt-assembly merge strategy
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

assemblyJarName in assembly := s"${name.value}.jar"
// Adds additional packages into Twirl
//TwirlKeys.templateImports += "com.example.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "com.example.binders._"
