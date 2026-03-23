package services

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory

import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.util.Try

final case class OgpMetadata(
  title: String,
  description: Option[String],
  imageUrl: Option[String],
  siteName: Option[String],
  fallback: Boolean
)

trait OgpClient {
  def fetch(url: String, anchorText: Option[String] = None): Option[OgpMetadata]
}

object NoOgpClient extends OgpClient {
  override def fetch(
    url: String,
    anchorText: Option[String]
  ): Option[OgpMetadata] =
    None
}

final class HttpOgpClient(
  timeout: Duration = Duration.ofSeconds(3),
  userAgent: String =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
) extends OgpClient {
  private val logger = LoggerFactory.getLogger(getClass)

  private val client =
    HttpClient
      .newBuilder()
      .connectTimeout(timeout)
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build()

  override def fetch(
    url: String,
    anchorText: Option[String]
  ): Option[OgpMetadata] =
    for {
      uri <- toUri(url)
      request <- buildRequest(uri)
      response <- send(request)
      if response.statusCode() / 100 == 2
      document <- parseDocument(response, uri)
      metadata =
        OgpMetadataParser.fromDocumentWithFallback(document, uri, anchorText)
      _ = logger.info(
        s"Ogp fetch url=$uri status=${response.statusCode()} title=${metadata.title} fallback=${metadata.fallback} image=${metadata.imageUrl.getOrElse("")}"
      )
    } yield metadata

  private def toUri(url: String): Option[URI] =
    Try(URI.create(url)).toOption

  private def buildRequest(uri: URI): Option[HttpRequest] =
    Try(
      HttpRequest
        .newBuilder(uri)
        .GET()
        .header("User-Agent", userAgent)
        .header(
          "Accept",
          "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        )
        .header("Accept-Language", "ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7")
        .timeout(timeout)
        .build()
    ).toOption

  private def send(request: HttpRequest): Option[HttpResponse[Array[Byte]]] =
    Try(client.send(request, HttpResponse.BodyHandlers.ofByteArray())).toOption

  private def parseDocument(
    response: HttpResponse[Array[Byte]],
    uri: URI
  ): Option[Document] =
    Try(
      Jsoup.parse(
        ByteArrayInputStream(response.body),
        detectCharset(response).orNull,
        uri.toString
      )
    ).toOption

  private def detectCharset(
    response: HttpResponse[Array[Byte]]
  ): Option[String] =
    response.headers
      .firstValue("content-type")
      .toScala
      .flatMap(
        _.split(";").iterator
          .map(_.trim)
          .find(_.toLowerCase(Locale.ROOT).startsWith("charset="))
          .map(_.substring("charset=".length).trim)
          .filter(_.nonEmpty)
      )
}

object OgpMetadataParser {
  private val logger = LoggerFactory.getLogger(getClass)

  private final case class SourceValue(source: String, value: String)

  private final case class ParseResult(
    title: SourceValue,
    description: Option[SourceValue],
    imageUrl: Option[SourceValue],
    siteName: Option[SourceValue],
    faviconUrl: Option[SourceValue],
    hasOgp: Boolean
  )

  private val titleSelectors =
    Seq(
      "meta[property=og:title]" -> "og:title",
      "meta[name=twitter:title]" -> "twitter:title",
      "meta[name=title]" -> "meta:title",
      "meta[property=title]" -> "meta:title",
      "meta[itemprop=name]" -> "meta:title"
    )

  private val descriptionSelectors =
    Seq(
      "meta[property=og:description]" -> "og:description",
      "meta[name=description]" -> "meta:description",
      "meta[name=twitter:description]" -> "twitter:description"
    )

  private val imageSelectors =
    Seq(
      "meta[property=og:image]" -> "og:image",
      "meta[name=twitter:image]" -> "twitter:image"
    )

  private val siteNameSelectors =
    Seq(
      "meta[property=og:site_name]" -> "og:site_name",
      "meta[name=application-name]" -> "meta:application-name"
    )

  private val faviconSelectors =
    Seq(
      """link[rel~=(?i)\bicon\b]""" -> "link:icon",
      """link[rel~=(?i)\bapple-touch-icon\b]""" -> "link:apple-touch-icon",
      """link[rel~=(?i)\bshortcut icon\b]""" -> "link:shortcut-icon"
    )

  def fromDocument(doc: Document, uri: URI): Option[OgpMetadata] =
    fromDocument(doc, uri, None)

  def fromDocument(
    doc: Document,
    uri: URI,
    anchorText: Option[String]
  ): Option[OgpMetadata] =
    parse(doc, anchorText).map { parsed =>
      val forceFallback = shouldForceFallback(uri)
      logParse(uri, parsed, forceFallback)
      if (forceFallback || !parsed.hasOgp) fallback(parsed, uri)
      else normal(parsed)
    }

  def fromDocumentWithFallback(doc: Document, uri: URI): OgpMetadata =
    fromDocumentWithFallback(doc, uri, None)

  def fromDocumentWithFallback(
    doc: Document,
    uri: URI,
    anchorText: Option[String]
  ): OgpMetadata =
    fromDocument(doc, uri, anchorText).getOrElse {
      logger.info(s"Ogp parse fallback-by-empty url=$uri")
      val anchorTitle = anchorText.map(_.trim).filter(_.nonEmpty)
      fallback(
        title = anchorTitle.orElse(htmlTitle(doc).map(_.value)),
        faviconUrl = pickHrefAbs(doc, faviconSelectors).map(_.value),
        uri = uri
      )
    }

  def fromHtml(html: String): Option[OgpMetadata] =
    for {
      uri <- Try(URI.create("https://example.invalid")).toOption
      doc <- Try(Jsoup.parse(html, uri.toString)).toOption
      parsed <- parse(doc, None)
    } yield normal(parsed)

  private def parse(
    doc: Document,
    anchorText: Option[String]
  ): Option[ParseResult] = {
    val anchorTitle =
      anchorText
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(SourceValue("anchor:text", _))
    val title =
      pickContent(doc, titleSelectors)
        .orElse(anchorTitle)
        .orElse(htmlTitle(doc))
    val description = pickContent(doc, descriptionSelectors)
    val imageUrl = pickAbsContent(doc, imageSelectors)
    val siteName = pickContent(doc, siteNameSelectors)
    val faviconUrl = pickHrefAbs(doc, faviconSelectors)
    val hasOgp =
      pickContent(doc, Seq("meta[property=og:title]" -> "og:title")).nonEmpty

    title.map { resolvedTitle =>
      ParseResult(
        title = resolvedTitle,
        description = description,
        imageUrl = imageUrl,
        siteName = siteName,
        faviconUrl = faviconUrl,
        hasOgp = hasOgp
      )
    }
  }

  private def normal(parsed: ParseResult): OgpMetadata =
    OgpMetadata(
      title = parsed.title.value,
      description = parsed.description.map(_.value),
      imageUrl = parsed.imageUrl.map(_.value),
      siteName = parsed.siteName.map(_.value),
      fallback = false
    )

  private def fallback(parsed: ParseResult, uri: URI): OgpMetadata =
    fallback(
      title = Some(parsed.title.value),
      faviconUrl = parsed.faviconUrl.map(_.value),
      uri = uri
    )

  private def fallback(
    title: Option[String],
    faviconUrl: Option[String],
    uri: URI
  ): OgpMetadata = {
    val host = Option(uri.getHost).getOrElse(uri.toString)
    val scheme = Option(uri.getScheme).getOrElse("https")
    val image = faviconUrl.getOrElse(s"$scheme://$host/favicon.ico")
    val resolvedTitle = title.map(_.trim).filter(_.nonEmpty).getOrElse(host)
    OgpMetadata(
      title = resolvedTitle,
      description = Some(uri.toString),
      imageUrl = Some(image),
      siteName = Some(host),
      fallback = true
    )
  }

  private def pickContent(
    doc: Document,
    selectors: Seq[(String, String)]
  ): Option[SourceValue] =
    selectors.view
      .flatMap { case (selector, source) =>
        Option(doc.selectFirst(selector))
          .map(elem => SourceValue(source, elem.attr("content").trim))
      }
      .find(_.value.nonEmpty)

  private def pickAbsContent(
    doc: Document,
    selectors: Seq[(String, String)]
  ): Option[SourceValue] = {
    selectors.view
      .flatMap { case (selector, source) =>
        Option(doc.selectFirst(selector)).map { elem =>
          SourceValue(source, Option(elem.absUrl("content")).getOrElse("").trim)
        }
      }
      .find(_.value.nonEmpty)
      .orElse(pickContent(doc, selectors))
  }

  private def pickHrefAbs(
    doc: Document,
    selectors: Seq[(String, String)]
  ): Option[SourceValue] =
    selectors.view
      .flatMap { case (selector, source) =>
        Option(doc.selectFirst(selector))
          .map(elem => SourceValue(source, elem.absUrl("href").trim))
      }
      .find(_.value.nonEmpty)

  private def htmlTitle(doc: Document): Option[SourceValue] =
    Option(doc.title)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(SourceValue("html:title", _))

  private def shouldForceFallback(uri: URI): Boolean = {
    val host = Option(uri.getHost).map(_.toLowerCase(Locale.ROOT)).getOrElse("")
    host == "amazon.co.jp" || host.endsWith(".amazon.co.jp")
  }

  private def logParse(
    uri: URI,
    parsed: ParseResult,
    forceFallback: Boolean
  ): Unit =
    logger.info(
      s"Ogp parse url=$uri og=${parsed.hasOgp} forceFallback=$forceFallback titleSource=${parsed.title.source} descriptionSource=${parsed.description.map(_.source).getOrElse("-")} imageSource=${parsed.imageUrl.map(_.source).getOrElse("-")} siteNameSource=${parsed.siteName.map(_.source).getOrElse("-")} faviconSource=${parsed.faviconUrl.map(_.source).getOrElse("-")} title=${parsed.title.value}"
    )
}

object ExternalLinkExtractor {
  def extractFromHtml(html: String): Seq[String] =
    Jsoup
      .parseBodyFragment(html)
      .select("a[href]")
      .asScala
      .toSeq
      .map(_.attr("href").trim)
      .filter(href => href.startsWith("http://") || href.startsWith("https://"))
      .distinct
}
