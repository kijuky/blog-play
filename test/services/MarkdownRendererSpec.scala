package services

import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Base64

class MarkdownRendererSpec extends AnyFunSuite {

  test("render autolinks plain URLs") {
    val baseDir = resourcePath("services/markdownrendererspec/blog")
    val renderer = new MarkdownRenderer(baseDir)

    val html = renderer.render("See https://example.com", contentPath = "01_test").body

    assert(html.contains("href=\"https://example.com\""))
  }

  test("render embeds relative images as data URIs") {
    val baseDir = resourcePath("services/markdownrendererspec/blog")
    val renderer = new MarkdownRenderer(baseDir)

    val markdown = "![fixture](img.png)"
    val html = renderer.render(markdown, contentPath = "01_test").body

    val bytes = Files.readAllBytes(baseDir.resolve("01_test").resolve("img.png"))
    val expectedBase64 = Base64.getEncoder.encodeToString(bytes)

    // probeContentType may return null depending on OS config, so accept either.
    assert(
        html.contains(s"data:image/png;base64,$expectedBase64") ||
          html.contains(s"data:application/octet-stream;base64,$expectedBase64")
    )
  }

  test("render decodes percent-encoded autolink display text") {
    val baseDir = resourcePath("services/markdownrendererspec/blog")
    val renderer = new MarkdownRenderer(baseDir)

    val markdown =
      "<https://unskilled.site/docker%E3%82%B3%E3%83%B3%E3%83%86%E3%83%8A%E3%81%AE%E4%B8%AD%E3%81%A7gui%E3%82%A2%E3%83%97%E3%83%AA%E3%82%B1%E3%83%BC%E3%82%B7%E3%83%A7%E3%83%B3%E3%82%92%E8%B5%B7%E5%8B%95%E3%81%95%E3%81%9B%E3%82%8B/>"
    val html = renderer.render(markdown, contentPath = "01_test").body

    assert(
      html.contains(
        "https://unskilled.site/dockerコンテナの中でguiアプリケーションを起動させる/"
      )
    )
    assert(
      html.contains(
        "href=\"https://unskilled.site/docker%E3%82%B3%E3%83%B3%E3%83%86%E3%83%8A%E3%81%AE%E4%B8%AD%E3%81%A7gui%E3%82%A2%E3%83%97%E3%83%AA%E3%82%B1%E3%83%BC%E3%82%B7%E3%83%A7%E3%83%B3%E3%82%92%E8%B5%B7%E5%8B%95%E3%81%95%E3%81%9B%E3%82%8B/\""
      )
    )
  }

  test("render keeps plus and converts decoded spaces back to plus in display text") {
    val baseDir = resourcePath("services/markdownrendererspec/blog")
    val renderer = new MarkdownRenderer(baseDir)

    val markdown =
      "<https://example.com/hello+world%20scala/>"
    val html = renderer.render(markdown, contentPath = "01_test").body

    assert(html.contains("https://example.com/hello+world+scala/"))
    assert(
      html.contains(
        "href=\"https://example.com/hello+world%20scala/\""
      )
    )
  }

  test("render code block with filename infers language from extension") {
    val baseDir = resourcePath("services/markdownrendererspec/blog")
    val renderer = new MarkdownRenderer(baseDir, Some(new StubHighlighter))

    val markdown =
      """```main.scala
        |val x = 1
        |```""".stripMargin
    val html = renderer.render(markdown, contentPath = "01_test").body

    assert(html.contains("""class="language-scala""""))
    assert(html.contains("""class="code-filename">main.scala"""))
  }

  test("render code block with sbt extension maps to scala") {
    val baseDir = resourcePath("services/markdownrendererspec/blog")
    val renderer = new MarkdownRenderer(baseDir, Some(new StubHighlighter))

    val markdown =
      """```build.sbt
        |val scalaVersion = "3.8.1"
        |```""".stripMargin
    val html = renderer.render(markdown, contentPath = "01_test").body

    assert(html.contains("""class="language-scala""""))
    assert(html.contains("""class="code-filename">build.sbt"""))
  }

  test("render code block with language and filename keeps both") {
    val baseDir = resourcePath("services/markdownrendererspec/blog")
    val renderer = new MarkdownRenderer(baseDir, Some(new StubHighlighter))

    val markdown =
      """```scala:Main.scala
        |val x = 1
        |```""".stripMargin
    val html = renderer.render(markdown, contentPath = "01_test").body

    assert(html.contains("""class="language-scala""""))
    assert(html.contains("""class="code-filename">Main.scala"""))
  }

  private def resourcePath(name: String): Path = {
    val url = Option(getClass.getClassLoader.getResource(name)).getOrElse {
      fail(s"Test resource not found: $name")
    }
    Paths.get(url.toURI)
  }
}

private final class StubHighlighter extends CodeHighlighter {
  override def highlight(code: String, language: Option[String]): Option[String] =
    Some("highlighted")
}
