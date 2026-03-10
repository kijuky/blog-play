name := "blog-play"

version := "0.1.0-SNAPSHOT"

scalaVersion := "3.8.1"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  "org.xerial" % "sqlite-jdbc" % "3.45.3.0"
)
