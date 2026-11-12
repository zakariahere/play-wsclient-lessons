name := """play-template"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.18"

libraryDependencies ++= Seq(guice, caffeine, ws, "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.0" % Test,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "org.mongodb.scala" %% "mongo-scala-driver" % "5.13.0",
  "org.typelevel" %% "cats-core" % "2.13.0")
