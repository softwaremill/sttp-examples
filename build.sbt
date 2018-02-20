import sbt._
import Keys._

val commonSettings = Seq(
  name := "sttp-examples",
  organization := "com.softwaremill.sttp",
  scalaVersion := "2.12.4",
  scalafmtOnCompile := true,
  scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xlint"),
  scalafmtVersion := "1.4.0",
  evictionWarningOptions in update := EvictionWarningOptions.default
    .withWarnTransitiveEvictions(false)
)

val akkaVersion = "2.5.9"
val akkaHttpVersion = "10.0.11"
val sttpVersion = "1.1.6-SNAPSHOT"

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .aggregate(
    zipkin
  )

lazy val zipkin: Project = (project in file("zipkin"))
  .settings(commonSettings: _*)
  .settings(
    name := "zipkin",
    libraryDependencies ++= Seq(
      // an explicit dependency is needed to evict the transitive one from akka-http
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.softwaremill.sttp" %% "akka-http-backend" % sttpVersion,
      "com.softwaremill.sttp" %% "brave-backend" % sttpVersion,
      "io.zipkin.reporter2" % "zipkin-sender-urlconnection" % "2.3.2"
    )
  ) 
