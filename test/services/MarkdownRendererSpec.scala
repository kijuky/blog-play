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

  private def resourcePath(name: String): Path = {
    val url = Option(getClass.getClassLoader.getResource(name)).getOrElse {
      fail(s"Test resource not found: $name")
    }
    Paths.get(url.toURI)
  }
}
