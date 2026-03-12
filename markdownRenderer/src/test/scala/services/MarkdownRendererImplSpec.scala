package services

import org.scalatest.funsuite.AnyFunSuite

import java.net.URL
import java.util.Base64
import scala.util.Using

class MarkdownRendererImplSpec extends AnyFunSuite {

  test("render autolinks plain URLs") {
    val sut = MarkdownRendererImpl()

    val actual = sut.render("See https://example.com")

    assert(actual.contains("href=\"https://example.com\""))
  }

  test("render embeds relative images as data URIs") {
    val contentUrl = resource("blog/01_test/README.md")
    val sut = MarkdownRendererImpl()

    val markdown = "![fixture](img.png)"
    val actual = sut.render(markdown, Some(contentUrl))

    val imgUrl = contentUrl.toURI.resolve("img.png").toURL
    val bytes = Using(imgUrl.openStream)(_.readAllBytes)
      .getOrElse { fail(s"Test resource not found: $imgUrl") }
    val expectedBase64 = Base64.getEncoder.encodeToString(bytes)

    // probeContentType may return null depending on OS config, so accept either.
    assert(
      actual.contains(s"data:image/png;base64,$expectedBase64") ||
        actual.contains(s"data:application/octet-stream;base64,$expectedBase64")
    )
  }

  test("render decodes percent-encoded autolink display text") {
    val sut = MarkdownRendererImpl()

    val markdown =
      "<https://unskilled.site/docker%E3%82%B3%E3%83%B3%E3%83%86%E3%83%8A%E3%81%AE%E4%B8%AD%E3%81%A7gui%E3%82%A2%E3%83%97%E3%83%AA%E3%82%B1%E3%83%BC%E3%82%B7%E3%83%A7%E3%83%B3%E3%82%92%E8%B5%B7%E5%8B%95%E3%81%95%E3%81%9B%E3%82%8B/>"
    val actual = sut.render(markdown)

    assert(
      actual.contains("https://unskilled.site/dockerコンテナの中でguiアプリケーションを起動させる/")
    )
    assert(
      actual.contains(
        "href=\"https://unskilled.site/docker%E3%82%B3%E3%83%B3%E3%83%86%E3%83%8A%E3%81%AE%E4%B8%AD%E3%81%A7gui%E3%82%A2%E3%83%97%E3%83%AA%E3%82%B1%E3%83%BC%E3%82%B7%E3%83%A7%E3%83%B3%E3%82%92%E8%B5%B7%E5%8B%95%E3%81%95%E3%81%9B%E3%82%8B/\""
      )
    )
  }

  test(
    "render keeps plus and converts decoded spaces back to plus in display text"
  ) {
    val sut = MarkdownRendererImpl()

    val markdown = "<https://example.com/hello+world%20scala/>"
    val actual = sut.render(markdown)

    assert(actual.contains("https://example.com/hello+world+scala/"))
    assert(actual.contains("href=\"https://example.com/hello+world%20scala/\""))
  }

  test("render code block with filename infers language from extension") {
    val sut = MarkdownRendererImpl(Some(StubHighlighter()))

    val markdown =
      """```main.scala
        |val x = 1
        |```""".stripMargin
    val actual = sut.render(markdown)

    assert(actual.contains("""class="language-scala""""))
    assert(actual.contains("""class="code-filename">main.scala"""))
  }

  test("render code block with sbt extension maps to scala") {
    val sut = MarkdownRendererImpl(Some(StubHighlighter()))

    val markdown =
      """```build.sbt
        |val scalaVersion = "3.8.1"
        |```""".stripMargin
    val actual = sut.render(markdown)

    assert(actual.contains("""class="language-scala""""))
    assert(actual.contains("""class="code-filename">build.sbt"""))
  }

  test("render code block with gs extension maps to javascript") {
    val sut = MarkdownRendererImpl(Some(StubHighlighter()))

    val markdown =
      """```code.gs
        |function main() {}
        |```""".stripMargin
    val actual = sut.render(markdown)

    assert(actual.contains("""class="language-javascript""""))
    assert(actual.contains("""class="code-filename">code.gs"""))
  }

  test("render code block with dotfile name maps by extension") {
    val sut = MarkdownRendererImpl(Some(StubHighlighter()))

    val markdown =
      """```.gitlab-ci.yml
        |stages:
        |  - test
        |```""".stripMargin
    val actual = sut.render(markdown)

    assert(actual.contains("""class="language-yaml""""))
    assert(actual.contains("""class="code-filename">.gitlab-ci.yml"""))
  }

  test("render mermaid code block as mermaid pre") {
    val sut = MarkdownRendererImpl(Some(StubHighlighter()))

    val markdown =
      """```mermaid
        |graph TD;
        |  A-->B;
        |```""".stripMargin
    val actual = sut.render(markdown)

    assert(actual.contains("""<pre class="mermaid">"""))
  }

  test("render code block with language and filename keeps both") {
    val sut = MarkdownRendererImpl(Some(StubHighlighter()))

    val markdown =
      """```scala:Main.scala
        |val x = 1
        |```""".stripMargin
    val actual = sut.render(markdown)

    assert(actual.contains("""class="language-scala""""))
    assert(actual.contains("""class="code-filename">Main.scala"""))
  }

  private def resource(name: String): URL = {
    val fullname = s"services/markdownrendererimplspec/$name"
    Option(getClass.getClassLoader.getResource(fullname))
      .getOrElse { fail(s"Test resource not found: $fullname") }
  }
}

private final class StubHighlighter extends CodeHighlighter {
  private val extensionMap =
    Map(
      "scala" -> "scala",
      "sbt" -> "scala",
      "gs" -> "javascript",
      "yml" -> "yaml"
    )

  override def highlight(
    code: String,
    language: Option[String]
  ): Option[String] =
    Some("highlighted")

  override def languageForExtension(ext: String): Option[String] =
    extensionMap.get(ext.toLowerCase)
}
