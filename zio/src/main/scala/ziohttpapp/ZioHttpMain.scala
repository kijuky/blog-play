package ziohttpapp

import com.typesafe.config.ConfigFactory
import io.getquill.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory
import org.virtuslab.yaml.*
import services.GrammarSpec
import services.MarkdownRenderer
import services.MarkdownRendererImpl
import services.Tm4eHighlighter
import zio.Task
import zio.ZIO
import zio.ZIOAppDefault
import zio.http.Body
import zio.http.Handler
import zio.http.Header
import zio.http.Headers
import zio.http.MediaType
import zio.http.Method
import zio.http.Path
import zio.http.Request
import zio.http.Response
import zio.http.Root
import zio.http.Routes
import zio.http.Server
import zio.http.Status
import zio.http.codec.PathCodec.trailing

import java.io.ByteArrayInputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Statement
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import scala.annotation.nowarn
import scala.io.Codec
import scala.io.Source
import scala.jdk.OptionConverters.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.Using

final case class BlogDateTime(zoneId: ZoneId, formatter: DateTimeFormatter) {
  def format(raw: Option[String]): String = {
    raw
      .map(OffsetDateTime.parse)
      .map(_.atZoneSameInstant(zoneId).format(formatter))
      .getOrElse("")
  }
}

object BlogDateTime {
  def from(zoneId: ZoneId, rawPattern: Option[String]): BlogDateTime = {
    BlogDateTime(
      zoneId,
      DateTimeFormatter.ofPattern(rawPattern.getOrElse("yyyy/MM/dd HH:mm"))
    )
  }
}

final case class BlogListRow(
  stableId: String,
  title: String,
  publishedAt: Option[String],
  modifiedAt: Option[String]
)

final case class BlogShowRow(
  stableId: String,
  title: String,
  body: String,
  publishedAt: Option[String],
  modifiedAt: Option[String]
)

final case class OgpPreview(
  url: String,
  title: String,
  description: Option[String],
  imageUrl: Option[String],
  siteName: Option[String],
  fallback: Boolean
)

object ZioHttpMain extends ZIOAppDefault {
  private val logger = LoggerFactory.getLogger(getClass)
  private val config = ConfigFactory.load()
  private val port = config.getInt("zio.http.port")
  private val ogpTimeout = Duration.ofSeconds(3)
  private val httpClient =
    HttpClient
      .newBuilder()
      .connectTimeout(ogpTimeout)
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build()
  private given ZoneId = ZoneId.systemDefault
  private val dateTime =
    BlogDateTime.from(
      summon[ZoneId],
      Option.when(config.hasPath("blog.datetime.format"))(
        config.getString("blog.datetime.format")
      )
    )

  private object Q extends H2JdbcContext(SnakeCase, "db.default")
  import Q.*
  private inline given SchemaMeta[BlogListRow] =
    schemaMeta(
      "blogs",
      _.stableId -> "stable_id",
      _.publishedAt -> "published_at",
      _.modifiedAt -> "modified_at"
    )
  private inline given SchemaMeta[BlogShowRow] =
    schemaMeta(
      "blogs",
      _.stableId -> "stable_id",
      _.publishedAt -> "published_at",
      _.modifiedAt -> "modified_at"
    )

  private val css =
    """
      |:root {
      |  --bg: #f6f0e9;
      |  --card: #fffaf5;
      |  --ink: #1f1a12;
      |  --muted: #6b5f52;
      |  --accent: #d25b2c;
      |}
      |body {
      |  margin: 0;
      |  padding: 24px;
      |  background: var(--bg);
      |  color: var(--ink);
      |  font-family: "Iowan Old Style", "Palatino", serif;
      |}
      |a { color: inherit; text-decoration: none; }
      |a:hover { color: var(--accent); }
      |.container { max-width: 980px; margin: 0 auto; }
      |.header { display: flex; justify-content: space-between; margin-bottom: 16px; }
      |.title { font-size: 32px; }
      |.subtitle { color: var(--muted); font-size: 14px; }
      |.card {
      |  background: var(--card);
      |  border: 1px solid rgba(25, 18, 12, 0.08);
      |  border-radius: 16px;
      |  padding: 20px;
      |}
      |.post-list { list-style: none; margin: 0; padding: 0; }
      |.post-item {
      |  display: grid;
      |  grid-template-columns: minmax(0, 1fr) auto;
      |  gap: 12px;
      |  padding: 12px 10px;
      |  border-bottom: 1px solid rgba(25, 18, 12, 0.08);
      |}
      |.post-item:last-child { border-bottom: 0; }
      |.post-title { min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
      |.post-date { color: var(--muted); font-size: 12px; }
      |.draft { margin-right: 8px; }
      |.post-body { line-height: 1.7; }
      |.post-body pre { overflow-x: auto; }
      |.post-body img { max-width: 100%; height: auto; }
      |.post-body .link-preview-inline { margin: 16px 0; }
      |.link-preview {
      |  display: grid;
      |  grid-template-columns: 112px minmax(0, 1fr);
      |  gap: 12px;
      |  border: 1px solid rgba(25, 18, 12, 0.12);
      |  border-radius: 12px;
      |  overflow: hidden;
      |  background: #fff;
      |}
      |.link-preview-image {
      |  width: 112px;
      |  height: 84px;
      |  object-fit: contain;
      |  display: block;
      |  margin: auto;
      |}
      |.link-preview-favicon {
      |  width: 32px;
      |  height: 32px;
      |}
      |.link-preview-content { padding: 10px 12px; min-width: 0; }
      |.link-preview-site { font-size: 12px; color: var(--muted); }
      |.link-preview-title { font-weight: 600; margin-top: 4px; }
      |.link-preview-description {
      |  margin-top: 4px;
      |  color: var(--muted);
      |  font-size: 13px;
      |  overflow: hidden;
      |  text-overflow: ellipsis;
      |  white-space: nowrap;
      |}
      |.youtube-embed { position: relative; width: 100%; padding-top: 56.25%; border-radius: 12px; overflow: hidden; }
      |.youtube-embed-frame {
      |  position: absolute;
      |  inset: 0;
      |  width: 100%;
      |  height: 100%;
      |  border: 0;
      |}
      |.back { margin-bottom: 14px; display: inline-block; }
      |""".stripMargin

  private val routes = Routes(
    Method.GET / Root -> Handler.fromZIO(listResponse()),
    Method.GET / "api" / "ogp" ->
      Handler.fromFunctionZIO[Request](request => ogpResponse(request)),
    Method.GET / "assets" / trailing ->
      Handler.fromFunction[(Path, Request)] { case (path, _) =>
        staticAssetResponse(path.toString)
      },
    Method.GET / "blog" / trailing ->
      Handler.fromFunctionZIO[(Path, Request)] { case (path, _) =>
        showResponse(path.toString)
      }
  )

  private def listResponse(): ZIO[Any, Nothing, Response] = {
    ZIO
      .attemptBlocking {
        val rows =
          Q.run(query[BlogListRow])
            .sortBy(row => row.publishedAt.orElse(row.modifiedAt))(using
              Ordering.Option[String].reverse
            )
        val body = rows.map { row =>
          val displayDate =
            dateTime.format(row.publishedAt.orElse(row.modifiedAt))
          val title =
            if (row.title.nonEmpty) escape(row.title) else "(untitled)"
          val draft =
            if (row.publishedAt.isEmpty)
              "<span class=\"draft\" title=\"Draft\">📝</span>"
            else
              ""
          s"""
               |<li class="post-item">
               |  <a class="post-title" href="/blog/${escapeAttr(
              row.stableId
            )}">$draft$title</a>
               |  <span class="post-date">${escape(displayDate)}</span>
               |</li>
               |""".stripMargin
        }.mkString

        htmlResponse(s"""
             |<div class="container">
             |  <header class="header">
             |    <div class="title">Blog Viewer (ZIO HTTP)</div>
             |    <div class="subtitle">Latest first</div>
             |  </header>
             |  <section class="card">
             |    <ul class="post-list">
             |      $body
             |    </ul>
             |  </section>
             |</div>
             |""".stripMargin)
      }
      .sandbox
      .fold(
        cause => {
          val details = formatFailure("listResponse", cause)
          logger.error(details)
          Response.text(details).status(Status.InternalServerError)
        },
        identity
      )
  }

  private def showResponse(stableId: String): ZIO[Any, Nothing, Response] = {
    ZIO
      .attemptBlocking {
        Q.run(query[BlogShowRow].filter(_.stableId == lift(stableId)))
          .headOption
      }
      .map {
        case None =>
          Response.text("Blog not found").status(Status.NotFound)
        case Some(row) =>
          val displayDate =
            dateTime.format(row.publishedAt.orElse(row.modifiedAt))
          val title =
            if (row.title.nonEmpty) escape(row.title) else "(untitled)"
          val draft =
            if (row.publishedAt.isEmpty)
              "<span class=\"draft\" title=\"Draft\">📝</span>"
            else
              ""
          htmlResponse(s"""
               |<div class="container">
               |  <a class="back" href="/">Back</a>
               |  <header class="header">
               |    <div class="title">$draft$title</div>
               |    <div class="subtitle">${escape(displayDate)}</div>
               |  </header>
               |  <section class="card post-body">
               |    ${row.body}
               |  </section>
               |</div>
               |<script async src="https://platform.twitter.com/widgets.js" charset="utf-8"></script>
               |<script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
               |<script>
               |  if (window.mermaid) {
               |    window.mermaid.initialize({ startOnLoad: true });
               |  }
               |</script>
               |<script src="/assets/javascripts/blog-ogp.js"></script>
              |""".stripMargin)
      }
      .sandbox
      .fold(
        cause => {
          val details = formatFailure(s"showResponse stableId=$stableId", cause)
          logger.error(details)
          Response.text(details).status(Status.InternalServerError)
        },
        identity
      )
  }

  private def formatFailure(
    label: String,
    cause: zio.Cause[Throwable]
  ): String = {
    val throwableText =
      cause.failureOption
        .orElse(cause.dieOption)
        .map(renderThrowableChain)
        .getOrElse(cause.prettyPrint)
    s"$label failed\n$throwableText"
  }

  private def renderThrowableChain(throwable: Throwable): String = {
    val chain =
      Iterator
        .iterate(Option(throwable))(_.flatMap(t => Option(t.getCause)))
        .takeWhile(_.nonEmpty)
        .flatten
        .toSeq
    val header =
      chain.zipWithIndex
        .map { case (t, index) =>
          val message = Option(t.getMessage).getOrElse("")
          s"#$index ${t.getClass.getName}${
              if (message.nonEmpty) s": $message" else ""
            }"
        }
        .mkString("\n")
    val stack = stackTrace(throwable)
    s"$header\n\n$stack"
  }

  private def stackTrace(throwable: Throwable): String = {
    val writer = StringWriter()
    val printer = PrintWriter(writer)
    throwable.printStackTrace(printer)
    printer.flush()
    writer.toString
  }

  private def htmlResponse(content: String): Response = {
    val html =
      s"""
         |<!DOCTYPE html>
         |<html lang="ja">
         |  <head>
         |    <meta charset="utf-8" />
         |    <meta name="viewport" content="width=device-width, initial-scale=1" />
         |    <title>Blog Viewer</title>
         |    <style>$css</style>
         |  </head>
         |  <body data-ogp-endpoint="/api/ogp">
         |    $content
         |  </body>
         |</html>
         |""".stripMargin
    Response(
      status = Status.Ok,
      headers = Headers(Header.ContentType(MediaType.text.html)),
      body = Body.fromString(html)
    )
  }

  private def ogpResponse(request: Request): ZIO[Any, Nothing, Response] =
    ZIO
      .attemptBlocking {
        val params = parseQueryParams(request.url.encode)
        val rawUrl = params.get("url").flatMap(_.headOption).getOrElse("")
        val anchorText =
          params
            .get("text")
            .flatMap(_.headOption)
            .map(_.trim)
            .filter(_.nonEmpty)

        validateUrl(rawUrl) match {
          case None =>
            Response.status(Status.BadRequest)
          case Some(validUrl) =>
            val preview =
              fetchOgp(validUrl, anchorText)
                .getOrElse(fallbackOgp(validUrl, anchorText))
            Response(
              status = Status.Ok,
              headers = Headers(Header.ContentType(MediaType.application.json)),
              body = Body.fromString(renderOgpJson(preview))
            )
        }
      }
      .catchAll { e =>
        logger.error(s"ogpResponse failed: ${e.getMessage}", e)
        ZIO.succeed(Response.status(Status.InternalServerError))
      }

  private def staticAssetResponse(path: String): Response = {
    val normalized = path.stripPrefix("/")
    normalized match {
      case "javascripts/blog-ogp.js" =>
        resourceTextResponse(
          "public/javascripts/blog-ogp.js",
          "application/javascript; charset=utf-8"
        )
      case _ =>
        Response.status(Status.NotFound)
    }
  }

  private def resourceTextResponse(
    resourcePath: String,
    contentType: String
  ): Response = {
    val classLoader = Thread.currentThread.getContextClassLoader
    Option(classLoader.getResourceAsStream(resourcePath)) match {
      case None =>
        Response.status(Status.NotFound)
      case Some(stream) =>
        val text =
          Using.resource(Source.fromInputStream(stream))(_.mkString)
        Response(
          status = Status.Ok,
          headers = Headers(Header.Custom("Content-Type", contentType)),
          body = Body.fromString(text)
        )
    }
  }

  private def escape(value: String): String = {
    value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&#39;")
  }

  private def escapeAttr(value: String): String = escape(value)

  private def validateUrl(raw: String): Option[String] =
    Try(URI.create(raw)).toOption
      .filter(_.getHost != null)
      .filter(uri => uri.getScheme == "http" || uri.getScheme == "https")
      .map(_.toString)

  private def fetchOgp(
    url: String,
    anchorText: Option[String]
  ): Option[OgpPreview] =
    for {
      uri <- Try(URI.create(url)).toOption
      request <-
        Try(
          HttpRequest
            .newBuilder(uri)
            .GET()
            .header(
              "User-Agent",
              "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
            )
            .header(
              "Accept",
              "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            .header("Accept-Language", "ja-JP,ja;q=0.9,en-US;q=0.8,en;q=0.7")
            .timeout(ogpTimeout)
            .build()
        ).toOption
      response <-
        Try(
          httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
        ).toOption
      if response.statusCode() / 100 == 2
      document <-
        Try(
          Jsoup.parse(
            ByteArrayInputStream(response.body),
            detectCharset(response).orNull,
            uri.toString
          )
        ).toOption
      preview <- parseOgp(document, uri, anchorText)
    } yield preview

  private def parseOgp(
    doc: Document,
    uri: URI,
    anchorText: Option[String]
  ): Option[OgpPreview] = {
    val title =
      pickMetaContent(doc, Seq("og:title", "twitter:title", "title"))
        .orElse(anchorText.map(_.trim).filter(_.nonEmpty))
        .orElse(Option(doc.title).map(_.trim).filter(_.nonEmpty))
    title.map { resolvedTitle =>
      val forceFallback = shouldForceFallback(uri)
      val hasOgp = pickMetaContent(doc, Seq("og:title")).nonEmpty
      val imageUrl =
        if (forceFallback || !hasOgp) {
          pickFavicon(doc).orElse(
            Some(s"${uri.getScheme}://${uri.getHost}/favicon.ico")
          )
        } else {
          pickMetaContent(doc, Seq("og:image", "twitter:image"))
            .flatMap(resolveAbsoluteUrl(uri, _))
        }
      val description =
        if (forceFallback || !hasOgp)
          Some(uri.toString)
        else
          pickMetaContent(
            doc,
            Seq("og:description", "description", "twitter:description")
          )
      val siteName =
        if (forceFallback || !hasOgp) Some(uri.getHost)
        else pickMetaContent(doc, Seq("og:site_name", "application-name"))
      OgpPreview(
        url = uri.toString,
        title = resolvedTitle,
        description = description,
        imageUrl = imageUrl,
        siteName = siteName,
        fallback = forceFallback || !hasOgp
      )
    }
  }

  private def fallbackOgp(
    url: String,
    anchorText: Option[String]
  ): OgpPreview = {
    val uri = URI.create(url)
    OgpPreview(
      url = url,
      title = anchorText.filter(_.nonEmpty).getOrElse(uri.getHost),
      description = Some(url),
      imageUrl = Some(s"${uri.getScheme}://${uri.getHost}/favicon.ico"),
      siteName = Some(uri.getHost),
      fallback = true
    )
  }

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

  private def shouldForceFallback(uri: URI): Boolean = {
    val host = Option(uri.getHost).map(_.toLowerCase(Locale.ROOT)).getOrElse("")
    host == "amazon.co.jp" || host.endsWith(".amazon.co.jp")
  }

  private def pickMetaContent(
    doc: Document,
    names: Seq[String]
  ): Option[String] = {
    val selectors =
      names.flatMap(name =>
        Seq(s"""meta[property="$name"]""", s"""meta[name="$name"]""")
      )
    selectors.view
      .flatMap(selector =>
        Option(doc.selectFirst(selector)).map(_.attr("content").trim)
      )
      .find(_.nonEmpty)
  }

  private def pickFavicon(doc: Document): Option[String] = {
    Seq(
      """link[rel~=(?i)\bicon\b]""",
      """link[rel~=(?i)\bapple-touch-icon\b]""",
      """link[rel~=(?i)\bshortcut icon\b]"""
    ).view
      .flatMap(selector =>
        Option(doc.selectFirst(selector)).map(_.absUrl("href").trim)
      )
      .find(_.nonEmpty)
  }

  private def resolveAbsoluteUrl(uri: URI, raw: String): Option[String] =
    Try(uri.resolve(raw).toString).toOption

  private def renderOgpJson(preview: OgpPreview): String = {
    val fields =
      Seq(
        Some(s""""url":"${jsonEscape(preview.url)}""""),
        Some(s""""title":"${jsonEscape(preview.title)}""""),
        preview.description.map(v => s""""description":"${jsonEscape(v)}""""),
        preview.imageUrl.map(v => s""""imageUrl":"${jsonEscape(v)}""""),
        preview.siteName.map(v => s""""siteName":"${jsonEscape(v)}""""),
        Some(s""""fallback":${preview.fallback}""")
      ).flatten
    s"{${fields.mkString(",")}}"
  }

  private def jsonEscape(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\b", "\\b")
      .replace("\f", "\\f")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")

  private def parseQueryParams(rawUrl: String): Map[String, Seq[String]] = {
    val qIndex = rawUrl.indexOf('?')
    if (qIndex < 0 || qIndex == rawUrl.length - 1)
      Map.empty
    else {
      val query = rawUrl.substring(qIndex + 1)
      query
        .split("&")
        .toSeq
        .filter(_.nonEmpty)
        .foldLeft[Map[String, Seq[String]]](Map.empty) { (acc, pair) =>
          val eqIndex = pair.indexOf('=')
          val rawKey =
            if (eqIndex < 0) pair else pair.substring(0, eqIndex)
          val rawValue =
            if (eqIndex < 0) "" else pair.substring(eqIndex + 1)
          val key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8)
          val value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8)
          val values = acc.getOrElse(key, Nil)
          acc.updated(key, values :+ value)
        }
    }
  }

  private def initRenderer(): MarkdownRenderer = {
    val classLoader = Thread.currentThread.getContextClassLoader
    val languages =
      Using(Source.fromResource("tm4e-lang.txt"))(_.getLines.toSeq)
        .fold(throw _, identity)

    val grammars =
      languages.flatMap(language =>
        GrammarSpec.from(language) match {
          case Success(v) =>
            Some(v)
          case Failure(e) =>
            logger.error(s"Failed to parse tm4e grammar: $language", e)
            None
        }
      )

    val maybeTheme = Option(classLoader.getResource("tm4e/theme.json"))
    maybeTheme match {
      case None =>
        MarkdownRendererImpl(None)
      case Some(themeUrl) =>
        MarkdownRendererImpl(Some(Tm4eHighlighter(grammars, themeUrl)))
    }
  }

  private def initDbAndImport(): Unit = {
    val db = config.getConfig("db.default")
    val driver = readRequiredString(db, Seq("driverClassName", "driver"))
    val url = readRequiredString(db, Seq("jdbcUrl", "url"))
    val user = readOptionalString(db, Seq("username", "user"))
    val password = Option.when(db.hasPath("password"))(db.getString("password"))
    Class.forName(driver)

    val renderer = initRenderer()
    val classLoader = Thread.currentThread.getContextClassLoader
    val initSql =
      Option(classLoader.getResourceAsStream("init.sql"))
        .map(stream =>
          Using.resource(Source.fromInputStream(stream))(_.mkString)
        )
        .getOrElse(throw RuntimeException("no such resource: init.sql"))

    withConnection(url, user, password) { conn =>
      conn.setAutoCommit(false)
      executeSqlScript(conn, initSql)

      val metaUrls =
        Using(Source.fromResource("blog.txt"))(
          _.getLines.map(getClass.getClassLoader.getResource).toSeq
        ).fold(throw _, identity)

      metaUrls.foreach(metaUrl =>
        importOne(conn, metaUrl, renderer)
          .fold(err => throw RuntimeException(err), identity)
      )
      conn.commit()
    }
  }

  private final case class Meta(
    title: Option[String],
    published_at: Option[String],
    modified_at: Option[String],
    tags: Option[Seq[String]]
  ) derives YamlDecoder

  private def importOne(
    conn: Connection,
    metaUrl: URL,
    renderer: MarkdownRenderer
  ): Either[String, Unit] = {
    readMeta(metaUrl).flatMap { meta =>
      @nowarn("msg=deprecated")
      val readmeUrl = URL(metaUrl, "README.md")
      for {
        source <- Right(resolveSource(metaUrl))
        stableId <- buildStableId(metaUrl, source)
        publishedAt <- normalizeDate(metaUrl, "published_at", meta.published_at)
        modifiedAt <- normalizeDate(metaUrl, "modified_at", meta.modified_at)
        body <-
          renderer
            .render(readmeUrl)
            .left
            .map(err => s"MissingBody($readmeUrl,$err)")
      } yield {
        val blogId =
          insertBlog(
            conn,
            stableId,
            meta.title.getOrElse(""),
            body,
            publishedAt,
            modifiedAt,
            source
          )
        meta.tags
          .getOrElse(Nil)
          .foreach(tag => {
            val tagId = findTagId(conn, tag).getOrElse(insertTag(conn, tag))
            insertBlogTag(conn, blogId, tagId)
          })
      }
    }
  }

  private def readMeta(metaUrl: URL): Either[String, Meta] = {
    Using(Source.fromURL(metaUrl))(_.mkString).toEither.left
      .map(err => s"MissingRoot($metaUrl,${err.getMessage})")
      .flatMap(_.as[Meta].left.map(err => s"ParseError($metaUrl,$err)"))
  }

  private def normalizeDate(
    metaUrl: URL,
    field: String,
    value: Option[String]
  ): Either[String, Option[String]] = {
    value match {
      case None =>
        Right(None)
      case Some(raw) =>
        parseInstant(raw)
          .map(i => Some(i.toString))
          .toRight(s"InvalidDate($metaUrl,$field,$raw)")
    }
  }

  private def parseInstant(raw: String): Option[Instant] = {
    Try(OffsetDateTime.parse(raw).toInstant)
      .orElse(Try(Instant.parse(raw)))
      .orElse(Try(LocalDateTime.parse(raw).atZone(summon[ZoneId]).toInstant))
      .toOption
  }

  private def resolveSource(metaUrl: URL): String = {
    metaUrl.getPath.split("/").reverse match {
      case Array("meta.yaml", _, source, "00_archive", _*) => source
      case _                                               => "github"
    }
  }

  private def buildStableId(
    metaUrl: URL,
    source: String
  ): Either[String, String] = {
    val parts = metaUrl.getPath.split("/").toSeq
    val dirName = parts.dropRight(1).lastOption.getOrElse("")
    val digitPrefix = dirName.takeWhile(_.isDigit)
    val underscorePrefix =
      dirName.indexOf('_') match {
        case i if i > 0 => dirName.substring(0, i)
        case _          => ""
      }

    val prefix =
      if (digitPrefix.nonEmpty) digitPrefix
      else if (underscorePrefix.nonEmpty) underscorePrefix
      else ""

    if (prefix.isEmpty)
      Left(s"DirectoryError($metaUrl,Invalid blog directory name: $dirName)")
    else
      Right(s"$source-$prefix")
  }

  private def insertBlog(
    conn: Connection,
    stableId: String,
    title: String,
    body: String,
    publishedAt: Option[String],
    modifiedAt: Option[String],
    source: String
  ): Long = {
    Using.resource(
      conn.prepareStatement(
        """
          |insert into blogs (stable_id, title, body, published_at, modified_at, source)
          |values (?, ?, ?, ?, ?, ?)
          |""".stripMargin,
        Statement.RETURN_GENERATED_KEYS
      )
    ) { stmt =>
      stmt.setString(1, stableId)
      stmt.setString(2, title)
      stmt.setString(3, body)
      stmt.setString(4, publishedAt.orNull)
      stmt.setString(5, modifiedAt.orNull)
      stmt.setString(6, source)
      stmt.executeUpdate()
      Using.resource(stmt.getGeneratedKeys) { keys =>
        if (keys.next()) keys.getLong(1)
        else throw RuntimeException("generated key missing")
      }
    }
  }

  private def findTagId(conn: Connection, name: String): Option[Long] = {
    Using.resource(
      conn.prepareStatement("select id from tags where name = ? limit 1")
    ) { stmt =>
      stmt.setString(1, name)
      Using.resource(stmt.executeQuery()) { rs =>
        Option.when(rs.next())(rs.getLong("id"))
      }
    }
  }

  private def insertTag(conn: Connection, name: String): Long = {
    Using.resource(
      conn.prepareStatement(
        "insert into tags (name) values (?)",
        Statement.RETURN_GENERATED_KEYS
      )
    ) { stmt =>
      stmt.setString(1, name)
      stmt.executeUpdate()
      Using.resource(stmt.getGeneratedKeys) { keys =>
        if (keys.next()) keys.getLong(1)
        else throw RuntimeException("generated key missing")
      }
    }
  }

  private def insertBlogTag(
    conn: Connection,
    blogId: Long,
    tagId: Long
  ): Unit = {
    Using.resource(conn.prepareStatement("""
          |insert into blog_tags (blog_id, tag_id)
          |select ?, ?
          |where not exists (
          |  select 1 from blog_tags where blog_id = ? and tag_id = ?
          |)
          |""".stripMargin)) { stmt =>
      stmt.setLong(1, blogId)
      stmt.setLong(2, tagId)
      stmt.setLong(3, blogId)
      stmt.setLong(4, tagId)
      stmt.executeUpdate()
      ()
    }
  }

  private def executeSqlScript(conn: Connection, script: String): Unit = {
    script
      .split(";")
      .map(_.trim)
      .filter(_.nonEmpty)
      .foreach(sql => Using.resource(conn.prepareStatement(sql))(_.execute()))
  }

  private def withConnection[A](
    url: String,
    user: Option[String],
    password: Option[String]
  )(f: Connection => A): A = {
    val connection =
      user match {
        case Some(u) =>
          DriverManager.getConnection(url, u, password.getOrElse(""))
        case None =>
          DriverManager.getConnection(url)
      }
    Using.resource(connection)(f)
  }

  private def readRequiredString(
    db: com.typesafe.config.Config,
    paths: Seq[String]
  ): String = {
    paths
      .collectFirst {
        case path if db.hasPath(path) => db.getString(path)
      }
      .getOrElse(
        throw RuntimeException(
          s"Missing config: one of ${paths.mkString(", ")}"
        )
      )
  }

  private def readOptionalString(
    db: com.typesafe.config.Config,
    paths: Seq[String]
  ): Option[String] = {
    paths
      .collectFirst {
        case path if db.hasPath(path) => db.getString(path)
      }
      .filter(_.nonEmpty)
  }

  override def run: Task[Unit] = {
    (ZIO.attempt(initDbAndImport()) *> Server.serve(routes))
      .provide(Server.defaultWithPort(port))
  }
}
