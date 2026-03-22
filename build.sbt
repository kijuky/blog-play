import java.nio.charset.StandardCharsets

ThisBuild / organization := "io.github.kijuky"
ThisBuild / scalaVersion := "3.8.1"

lazy val blog =
  project
    .in(file("blog"))
    .settings(
      // project
      name := "blog",

      // resource list
      Compile / resourceGenerators += Def.task {
        val outDir = (Compile / resourceManaged).value
        val out = outDir / "blog.txt"
        val root = (Compile / resourceDirectory).value / "blog"
        val cacheBase = streams.value.cacheDirectory / "blog-cache"
        val inputs = (root ** "meta.yaml").get.toSet
        val cachedGen =
          FileFunction.cached(
            cacheBase,
            inStyle = FilesInfo.hash,
            outStyle = FilesInfo.exists
          ) { _ =>
            val lines =
              inputs.toSeq
                .flatMap(IO.relativize((Compile / resourceDirectory).value, _))
                .map(_.replace('\\', '/'))
                .sorted
            IO.writeLines(out, lines, StandardCharsets.UTF_8)
            Set(out)
          }
        cachedGen(inputs).toSeq
      }.taskValue,
      Compile / products :=
        (Compile / products).dependsOn(Compile / resources).value
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
      ),

      // resource list
      Compile / resourceGenerators += Def.task {
        val outDir = (Compile / resourceManaged).value
        val out = outDir / "tm4e-lang.txt"
        val root = (Compile / resourceDirectory).value / "tm4e" / "lang"
        val cacheBase = streams.value.cacheDirectory / "tm4e-lang-cache"
        val inputs = (root ** "*.json").get.toSet
        val cachedGen =
          FileFunction.cached(
            cacheBase,
            inStyle = FilesInfo.hash,
            outStyle = FilesInfo.exists
          ) { _ =>
            val lines =
              inputs.toSeq
                .flatMap(IO.relativize((Compile / resourceDirectory).value, _))
                .map(_.replace('\\', '/'))
                .sorted
            IO.writeLines(out, lines, StandardCharsets.UTF_8)
            Set(out)
          }
        cachedGen(inputs).toSeq
      }.taskValue,
      Compile / products :=
        (Compile / products).dependsOn(Compile / resources).value
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
        "-J-Xmx12G",
        "--allow-incomplete-classpath",
        "--enable-http",
        "--install-exit-handlers",
        "--no-fallback"
      )
    )

lazy val zio =
  project
    .in(file("zio"))
    .dependsOn(blog, markdownRenderer)
    .settings(
      // project
      name := "blog-zio",

      // libraries
      libraryDependencies ++= Seq(
        "com.h2database" % "h2" % "2.4.240",
        "com.typesafe" % "config" % "1.4.6",
        "dev.zio" %% "zio-http" % "3.10.1",
        "io.getquill" %% "quill-jdbc" % "4.8.6",
        "org.slf4j" % "slf4j-api" % "2.0.17",
        "org.virtuslab" %% "scala-yaml" % "0.3.1"
      )
    )

lazy val root =
  project
    .in(file("."))
    .aggregate(blog, markdownRenderer, play, zio)
