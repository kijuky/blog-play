name := "blog-play"

version := "0.1.0-SNAPSHOT"

scalaVersion := "3.8.1"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala, DockerPlugin)

Assets / pipelineStages := Seq(digest, gzip)

libraryDependencies ++= Seq(
  filters,
  "com.h2database" % "h2" % "2.4.240",
  "com.typesafe" % "config" % "1.4.6",
  "org.commonmark" % "commonmark" % "0.27.1",
  "org.commonmark" % "commonmark-ext-autolink" % "0.27.1",
  "org.commonmark" % "commonmark-ext-gfm-tables" % "0.27.1",
  "org.eclipse" % "org.eclipse.tm4e.core" % "0.17.2-SNAPSHOT",
  "org.jruby.joni" % "joni" % "2.2.3" % Runtime,
  "org.jruby.jcodings" % "jcodings" % "1.0.60" % Runtime,
  "org.scalikejdbc" %% "scalikejdbc" % "4.3.5",
  "org.scalikejdbc" %% "scalikejdbc-config" % "4.3.5",
  "org.virtuslab" %% "scala-yaml" % "0.3.1",
  "org.scalatest" %% "scalatest-funsuite" % "3.2.19" % Test
)

resolvers += "tm4e-snapshots" at "https://repo.eclipse.org/content/repositories/tm4e-snapshots/"

// sbt-native-packager (Docker) settings
dockerBaseImage := "eclipse-temurin:21-jre-jammy"
dockerExposedPorts := Seq(9000)
dockerEnvVars ++= Map(
  "TZ" -> "Asia/Tokyo"
)

// Don't create RUNNING_PID inside the container image (Play prod default).
// Official workaround is to point it at /dev/null via JVM option.
Universal / javaOptions += "-Dpidfile.path=/dev/null"
