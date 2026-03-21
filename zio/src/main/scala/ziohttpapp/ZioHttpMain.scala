package ziohttpapp

import com.typesafe.config.ConfigFactory
import io.getquill.*
import io.github.classgraph.ClassGraph
import org.slf4j.LoggerFactory
import org.virtuslab.yaml.*
import services.GrammarSpec
import services.MarkdownRenderer
import services.MarkdownRendererImpl
import services.Tm4eHighlighter
import zio.Task
import zio.ZIO
import zio.ZIOAppDefault
import zio.http.Handler
import zio.http.Method
import zio.http.Path
import zio.http.Request
import zio.http.Response
import zio.http.Root
import zio.http.Routes
import zio.http.Server
import zio.http.Status
import zio.http.codec.PathCodec.trailing

import java.net.URL
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Statement
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import scala.io.Codec
import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.Using

final case class BlogDateTime(zoneId: ZoneId, formatter: DateTimeFormatter) {
  def format(raw: Option[String]): String = {
    raw
      .map(OffsetDateTime.parse)
      .map(dt => formatter.format(dt.atZoneSameInstant(zoneId)))
      .getOrElse("")
  }
}

object BlogDateTime {
  def from(zoneId: ZoneId, rawPattern: Option[String]): BlogDateTime = {
    BlogDateTime(zoneId, DateTimeFormatter.ofPattern(rawPattern.getOrElse("yyyy/MM/dd HH:mm")))
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

object ZioHttpMain extends ZIOAppDefault {
  private val logger = LoggerFactory.getLogger(getClass)
  private val config = ConfigFactory.load()
  private val port = config.getInt("zio.http.port")
  private given ZoneId = ZoneId.systemDefault()
  private val dateTime =
    BlogDateTime.from(
      summon[ZoneId],
      Option.when(config.hasPath("blog.datetime.format"))(config.getString("blog.datetime.format"))
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
      |.back { margin-bottom: 14px; display: inline-block; }
      |""".stripMargin

  private val routes = Routes(
    Method.GET / Root -> Handler.fromZIO(listResponse()),
    Method.GET / "blog" / trailing -> Handler.fromFunctionZIO[(Path, Request)] {
      case (path, _) =>
        showResponse(path.toString)
    }
  )

  private def listResponse(): ZIO[Any, Nothing, Response] = {
    ZIO
      .attemptBlocking {
        val rows =
          Q.run(query[BlogListRow])
            .sortBy(row => row.publishedAt.orElse(row.modifiedAt))(using Ordering.Option[String].reverse)
        val body = rows
          .map { row =>
            val displayDate = dateTime.format(row.publishedAt.orElse(row.modifiedAt))
            val title = if (row.title.nonEmpty) escape(row.title) else "(untitled)"
            val draft = if (row.publishedAt.isEmpty) "<span class=\"draft\" title=\"Draft\">📝</span>" else ""
            s"""
               |<li class="post-item">
               |  <a class="post-title" href="/blog/${escapeAttr(row.stableId)}">${draft}${title}</a>
               |  <span class="post-date">${escape(displayDate)}</span>
               |</li>
               |""".stripMargin
          }
          .mkString

        htmlResponse(
          s"""
             |<div class="container">
             |  <header class="header">
             |    <div class="title">Blog Viewer (ZIO HTTP)</div>
             |    <div class="subtitle">Latest first</div>
             |  </header>
             |  <section class="card">
             |    <ul class="post-list">
             |      ${body}
             |    </ul>
             |  </section>
             |</div>
             |""".stripMargin
        )
      }
      .catchAll(err => ZIO.succeed(Response.text(err.getMessage).status(Status.InternalServerError)))
  }

  private def showResponse(stableId: String): ZIO[Any, Nothing, Response] = {
    ZIO
      .attemptBlocking {
        Q.run(query[BlogShowRow].filter(_.stableId == lift(stableId))).headOption
      }
      .map {
        case None =>
          Response.text("Blog not found").status(Status.NotFound)
        case Some(row) =>
          val displayDate = dateTime.format(row.publishedAt.orElse(row.modifiedAt))
          val title = if (row.title.nonEmpty) escape(row.title) else "(untitled)"
          val draft = if (row.publishedAt.isEmpty) "<span class=\"draft\" title=\"Draft\">📝</span>" else ""
          htmlResponse(
            s"""
               |<div class="container">
               |  <a class="back" href="/">Back</a>
               |  <header class="header">
               |    <div class="title">${draft}${title}</div>
               |    <div class="subtitle">${escape(displayDate)}</div>
               |  </header>
               |  <section class="card post-body">
               |    ${row.body}
               |  </section>
               |</div>
               |<script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
               |<script>
               |  if (window.mermaid) {
               |    window.mermaid.initialize({ startOnLoad: true });
               |  }
               |</script>
               |""".stripMargin
          )
      }
      .catchAll(err => ZIO.succeed(Response.text(err.getMessage).status(Status.InternalServerError)))
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
         |    <style>${css}</style>
         |  </head>
         |  <body>
         |    ${content}
         |  </body>
         |</html>
         |""".stripMargin
    Response.html(html)
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

  private def initRenderer(): MarkdownRenderer = {
    val classLoader = Thread.currentThread.getContextClassLoader
    val languages =
      Using(ClassGraph().acceptPaths("tm4e/lang").scan())(
        _.getAllResources
          .filter(_.getPath.endsWith(".json"))
          .asScala
          .toSeq
      ).fold(throw _, identity)

    val grammars =
      languages.flatMap(language =>
        GrammarSpec.from(language.getPath) match {
          case Success(v) => Some(v)
          case Failure(e) =>
            logger.error(s"Failed to parse tm4e grammar: ${language.getPath}", e)
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
    val driver = db.getString("driver")
    val url = db.getString("url")
    val user = if (db.hasPath("user")) db.getString("user") else "sa"
    val password = if (db.hasPath("password")) db.getString("password") else ""
    Class.forName(driver)

    val renderer = initRenderer()
    val classLoader = Thread.currentThread.getContextClassLoader
    val initSql =
      Option(classLoader.getResourceAsStream("init.sql"))
        .map(stream => Using.resource(Source.fromInputStream(stream))(src => src.mkString))
        .getOrElse(throw RuntimeException("no such resource: init.sql"))

    withConnection(url, user, password) { conn =>
      conn.setAutoCommit(false)
      executeSqlScript(conn, initSql)

      val metaUrls =
        Using(ClassGraph().acceptPaths("blog").scan())(
          _.getAllResources
            .filter(_.getPath.endsWith("meta.yaml"))
            .asScala
            .map(_.getURL)
            .toSeq
        ).fold(throw _, identity)

      metaUrls.foreach(metaUrl => importOne(conn, metaUrl, renderer).fold(err => throw RuntimeException(err), identity))
      conn.commit()
    }
  }

  private final case class Meta(
    title: Option[String],
    published_at: Option[String],
    modified_at: Option[String],
    tags: Option[Seq[String]]
  ) derives YamlDecoder

  private def importOne(conn: Connection, metaUrl: URL, renderer: MarkdownRenderer): Either[String, Unit] = {
    readMeta(metaUrl).flatMap { meta =>
      val readmeUrl = URL(metaUrl, "README.md")
      for {
        source <- Right(resolveSource(metaUrl))
        stableId <- buildStableId(metaUrl, source)
        publishedAt <- normalizeDate(metaUrl, "published_at", meta.published_at)
        modifiedAt <- normalizeDate(metaUrl, "modified_at", meta.modified_at)
        body <- renderer.render(readmeUrl).left.map(err => s"MissingBody(${readmeUrl},${err})")
      } yield {
        val blogId = insertBlog(conn, stableId, meta.title.getOrElse(""), body, publishedAt, modifiedAt, source)
        meta.tags.getOrElse(Nil).foreach(tag => {
          val tagId = findTagId(conn, tag).getOrElse(insertTag(conn, tag))
          insertBlogTag(conn, blogId, tagId)
        })
      }
    }
  }

  private def readMeta(metaUrl: URL): Either[String, Meta] = {
    Using(Source.fromURL(metaUrl))(_.mkString)
      .toEither
      .left
      .map(err => s"MissingRoot(${metaUrl},${err.getMessage})")
      .flatMap(text => text.as[Meta].left.map(err => s"ParseError(${metaUrl},${err})"))
  }

  private def normalizeDate(metaUrl: URL, field: String, value: Option[String]): Either[String, Option[String]] = {
    value match {
      case None =>
        Right(None)
      case Some(raw) =>
        parseInstant(raw)
          .map(i => Some(i.toString))
          .toRight(s"InvalidDate(${metaUrl},${field},${raw})")
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
      case _                                                 => "github"
    }
  }

  private def buildStableId(metaUrl: URL, source: String): Either[String, String] = {
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

    if (prefix.isEmpty) Left(s"DirectoryError(${metaUrl},Invalid blog directory name: ${dirName})")
    else Right(s"${source}-${prefix}")
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
        if (keys.next()) keys.getLong(1) else throw RuntimeException("generated key missing")
      }
    }
  }

  private def findTagId(conn: Connection, name: String): Option[Long] = {
    Using.resource(conn.prepareStatement("select id from tags where name = ? limit 1")) { stmt =>
      stmt.setString(1, name)
      Using.resource(stmt.executeQuery()) { rs =>
        if (rs.next()) Some(rs.getLong("id")) else None
      }
    }
  }

  private def insertTag(conn: Connection, name: String): Long = {
    Using.resource(
      conn.prepareStatement("insert into tags (name) values (?)", Statement.RETURN_GENERATED_KEYS)
    ) { stmt =>
      stmt.setString(1, name)
      stmt.executeUpdate()
      Using.resource(stmt.getGeneratedKeys) { keys =>
        if (keys.next()) keys.getLong(1) else throw RuntimeException("generated key missing")
      }
    }
  }

  private def insertBlogTag(conn: Connection, blogId: Long, tagId: Long): Unit = {
    Using.resource(
      conn.prepareStatement(
        """
          |insert into blog_tags (blog_id, tag_id)
          |select ?, ?
          |where not exists (
          |  select 1 from blog_tags where blog_id = ? and tag_id = ?
          |)
          |""".stripMargin
      )
    ) { stmt =>
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
      .foreach(sql => {
        Using.resource(conn.prepareStatement(sql))(_.execute())
      })
  }

  private def withConnection[A](url: String, user: String, password: String)(f: Connection => A): A = {
    Using.resource(DriverManager.getConnection(url, user, password))(f)
  }

  override def run: Task[Unit] = {
    ZIO
      .attempt(initDbAndImport())
      .flatMap(_ => Server.serve(routes))
      .provide(Server.defaultWithPort(port))
  }
}
