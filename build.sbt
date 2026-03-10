name := "blog-play"

version := "0.1.0-SNAPSHOT"

scalaVersion := "3.8.1"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  "org.xerial" % "sqlite-jdbc" % "3.45.3.0",
  "org.scalikejdbc" %% "scalikejdbc" % "4.3.5",
  "org.scalikejdbc" %% "scalikejdbc-config" % "4.3.5",
  "org.scalatest" %% "scalatest-funsuite" % "3.2.19" % Test
)
