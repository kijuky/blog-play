package services

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.scalatest.funsuite.AnyFunSuite

import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.Charset
import java.time.Duration

class OgpClientSpec extends AnyFunSuite {
  test("OgpMetadataParser reads og meta tags") {
    val html =
      """
        |<html>
        |  <head>
        |    <meta property="og:title" content="Title" />
        |    <meta property="og:description" content="Description" />
        |    <meta property="og:image" content="https://example.com/image.png" />
        |    <meta property="og:site_name" content="Example" />
        |  </head>
        |</html>
        |""".stripMargin

    val actual = OgpMetadataParser.fromHtml(html)
    assert(
      actual.contains(
        OgpMetadata(
          "Title",
          Some("Description"),
          Some("https://example.com/image.png"),
          Some("Example"),
          false
        )
      )
    )
  }

  test("force fallback only for amazon.co.jp hosts") {
    val html =
      """
        |<html>
        |  <head>
        |    <meta property="og:title" content="OG Title" />
        |    <meta property="og:image" content="https://img.example/og.png" />
        |    <link rel="icon" href="/favicon.ico" />
        |  </head>
        |</html>
        |""".stripMargin
    val amazonDoc = org.jsoup.Jsoup.parse(html, "https://www.amazon.co.jp/item")
    val nonAmazonDoc =
      org.jsoup.Jsoup.parse(html, "https://www.amazon.com/item")

    val amazon =
      OgpMetadataParser.fromDocumentWithFallback(
        amazonDoc,
        URI.create("https://www.amazon.co.jp/item")
      )
    val nonAmazon =
      OgpMetadataParser.fromDocumentWithFallback(
        nonAmazonDoc,
        URI.create("https://www.amazon.com/item")
      )

    assert(amazon.fallback)
    assert(amazon.imageUrl.contains("https://www.amazon.co.jp/favicon.ico"))
    assert(!nonAmazon.fallback)
    assert(nonAmazon.imageUrl.contains("https://img.example/og.png"))
  }

  test("fallback title prefers meta title over html title") {
    val html =
      """
        |<html>
        |  <head>
        |    <meta name="title" content="Meta Title" />
        |    <title>HTML Title</title>
        |  </head>
        |</html>
        |""".stripMargin
    val doc = org.jsoup.Jsoup.parse(html, "https://example.com/post")

    val actual =
      OgpMetadataParser.fromDocumentWithFallback(
        doc,
        URI.create("https://example.com/post")
      )

    assert(actual.fallback)
    assert(actual.title == "Meta Title")
  }

  test("fallback title prefers anchor text over html title") {
    val html =
      """
        |<html>
        |  <head>
        |    <title>HTML Title</title>
        |  </head>
        |</html>
        |""".stripMargin
    val doc = org.jsoup.Jsoup.parse(html, "https://example.com/post")

    val actual =
      OgpMetadataParser.fromDocumentWithFallback(
        doc,
        URI.create("https://example.com/post"),
        Some("Anchor Text")
      )

    assert(actual.fallback)
    assert(actual.title == "Anchor Text")
  }

  test("fallback image prefers link rel icon") {
    val html =
      """
        |<html>
        |  <head>
        |    <meta name="title" content="Title" />
        |    <link rel="icon" href="https://cdn.example.com/favicon.png" />
        |  </head>
        |</html>
        |""".stripMargin
    val doc = org.jsoup.Jsoup.parse(html, "https://example.com/post")

    val actual =
      OgpMetadataParser.fromDocumentWithFallback(
        doc,
        URI.create("https://example.com/post")
      )

    assert(actual.fallback)
    assert(actual.imageUrl.contains("https://cdn.example.com/favicon.png"))
  }

  test("HttpOgpClient decodes response body with charset from content-type") {
    val title = "ディノス"
    val html =
      s"""
        |<html>
        |  <head>
        |    <meta name="title" content="$title" />
        |  </head>
        |</html>
        |""".stripMargin
    val bytes = html.getBytes(Charset.forName("Shift_JIS"))
    val server = HttpServer.create(InetSocketAddress(0), 0)
    server.createContext(
      "/",
      exchange => {
        exchange.getResponseHeaders
          .add("Content-Type", "text/html; charset=Shift_JIS")
        exchange.sendResponseHeaders(200, bytes.length.toLong)
        val os = exchange.getResponseBody
        os.write(bytes)
        os.close()
      }
    )
    server.start()

    try {
      val port = server.getAddress.getPort
      val sut = HttpOgpClient(timeout = Duration.ofSeconds(2))
      val actual = sut.fetch(s"http://127.0.0.1:$port/")
      assert(actual.exists(_.title == title))
    } finally {
      server.stop(0)
    }
  }

  test("ExternalLinkExtractor extracts only absolute links") {
    val html =
      """
        |<p>
        |  <a href="https://example.com/a">A</a>
        |  <a href="http://example.com/b">B</a>
        |  <a href="/relative">R</a>
        |</p>
        |""".stripMargin

    val actual = ExternalLinkExtractor.extractFromHtml(html)
    assert(actual == Seq("https://example.com/a", "http://example.com/b"))
  }
}
