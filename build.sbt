ThisBuild / organization := "io.github.kijuky"
ThisBuild / scalaVersion := "3.8.1"

lazy val blog =
  project
    .in(file("blog"))
    .settings(
      // project
      name := "blog",

      // libraries
      libraryDependencies ++= Seq(
        "io.github.classgraph" % "classgraph" % "4.8.184"
      )
    )

lazy val markdownRenderer =
  project
    .in(file("markdownRenderer"))
    .settings(
      // project
      name := "markdown-renderer",

      // libraries
      resolvers += "tm4e-snapshots" at "https://repo.eclipse.org/content/repositories/tm4e-snapshots/",
      libraryDependencies ++= Seq(
        "com.typesafe" % "config" % "1.4.6",
        "org.commonmark" % "commonmark" % "0.27.1",
        "org.commonmark" % "commonmark-ext-autolink" % "0.27.1",
        "org.commonmark" % "commonmark-ext-gfm-tables" % "0.27.1",
        "org.eclipse" % "org.eclipse.tm4e.core" % "0.17.2-SNAPSHOT",
        "org.jruby.joni" % "joni" % "2.2.7" % Runtime,
        "org.jruby.jcodings" % "jcodings" % "1.0.64" % Runtime,
        "org.scalatest" %% "scalatest-funsuite" % "3.2.17" % Test,
        "org.slf4j" % "slf4j-api" % "2.0.17"
      )
    )

lazy val play =
  project
    .in(file("play"))
    .dependsOn(blog, markdownRenderer)
    .enablePlugins(PlayScala)
    .settings(
      // project
      name := "blog-play",

      // libraries
      libraryDependencies ++=
        Seq(
          filters,
          "com.h2database" % "h2" % "2.4.240",
          "org.webjars.npm" % "mermaid" % "11.2.1",
          "org.scalikejdbc" %% "scalikejdbc" % "4.3.5",
          "org.scalikejdbc" %% "scalikejdbc-config" % "4.3.5",
          "org.virtuslab" %% "scala-yaml" % "0.3.1",
          "org.scalatest" %% "scalatest-funsuite" % "3.2.17" % Test
        ),

      // pipeline
      Assets / pipelineStages := Seq(filter, cssCompress, digest, gzip),

      // npm系webjarsの展開を抑制する。
      filter / includeFilter :=
        ".*/(images|javascripts|stylesheets)/.*".r ||
          "mermaid.min.js"
    )
    // sbt-native-packager (Docker) settings
    .enablePlugins(DockerPlugin)
    .settings(
      dockerBaseImage := "eclipse-temurin:25.0.2_10-jre-jammy",
      dockerExposedPorts := Seq(9000),
      dockerEnvVars ++= Map("TZ" -> "Asia/Tokyo"),

      // Don't create RUNNING_PID inside the container image (Play prod default).
      // Official workaround is to point it at /dev/null via JVM option.
      Universal / javaOptions += "-Dpidfile.path=/dev/null"
    )
    // sbt-native-packager (GraalVM) settings
    .enablePlugins(GraalVMNativeImagePlugin)
    .settings(
      PlayKeys.externalizeResources := false,
      graalVMNativeImageOptions ++= Seq(
        "-J-Xmx10G",
        "--allow-incomplete-classpath",
        "--enable-http",
        "--install-exit-handlers",
        "--no-fallback"
      )
    )

lazy val root =
  project
    .in(file("."))
    .aggregate(blog, markdownRenderer, play)
