name := "blog-play"

version := "0.1.0-SNAPSHOT"

scalaVersion := "3.8.1"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  "org.xerial" % "sqlite-jdbc" % "3.51.2.0",
  "org.scalikejdbc" %% "scalikejdbc" % "4.3.5",
  "org.scalikejdbc" %% "scalikejdbc-config" % "4.3.5",
  "org.commonmark" % "commonmark" % "0.27.1",
  "org.commonmark" % "commonmark-ext-autolink" % "0.27.1",
  "org.virtuslab" %% "scala-yaml" % "0.3.1",
  "org.scalatest" %% "scalatest-funsuite" % "3.2.19" % Test
)
