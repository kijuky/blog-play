package ziohttpapp

import com.typesafe.config.ConfigFactory
import io.github.classgraph.ClassGraph
import org.slf4j.LoggerFactory
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.SQL
import scalikejdbc.WrappedResultSet
import scalikejdbc.config.DBs
import services.BlogImporter
import services.DbInitializer
import services.GrammarSpec
import services.MarkdownRendererImpl
import services.SqlLogging
import services.Tm4eHighlighter
import zio.Task
import zio.ZIO
import zio.ZIOAppDefault
import zio.http.Method
import zio.http.Handler
import zio.http.Path
import zio.http.Request
import zio.http.Response
import zio.http.Root
import zio.http.Routes
import zio.http.Server
import zio.http.Status
import zio.http.codec.PathCodec.trailing

import java.time.OffsetDateTime
import java.time.ZoneId
import scala.jdk.CollectionConverters.*
import scala.util.Failure
import scala.util.Success
import scala.util.Using

final case class ZioBlogListItem(
  stableId: String,
  title: String,
  publishedAt: Option[OffsetDateTime],
  modifiedAt: Option[OffsetDateTime]
)

object ZioBlogListItem {
  def from(rs: WrappedResultSet): ZioBlogListItem = {
    apply(
      rs.string("stable_id"),
      rs.string("title"),
      rs.stringOpt("published_at").map(OffsetDateTime.parse),
      rs.stringOpt("modified_at").map(OffsetDateTime.parse)
    )
  }
}

final case class ZioBlogShowItem(
  stableId: String,
  title: String,
  body: String,
  publishedAt: Option[OffsetDateTime],
  modifiedAt: Option[OffsetDateTime]
)

object ZioBlogShowItem {
  def from(rs: WrappedResultSet): ZioBlogShowItem = {
    apply(
      rs.string("stable_id"),
      rs.string("title"),
      rs.string("body"),
      rs.stringOpt("published_at").map(OffsetDateTime.parse),
      rs.stringOpt("modified_at").map(OffsetDateTime.parse)
    )
  }
}

object ZioHttpMain extends ZIOAppDefault {
  private val logger = LoggerFactory.getLogger(getClass)
  private val config = ConfigFactory.load()
  private val port = config.getInt("zio.http.port")
  private given ZoneId = ZoneId.systemDefault()
  private given controllers.BlogDateTime = controllers.BlogDateTime.from(
    summon[ZoneId],
    Option.when(config.hasPath("blog.datetime.format"))(config.getString("blog.datetime.format"))
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
    Method.GET / Root -> Handler.fromZIO(toResponse(listResponse())),
    Method.GET / "blog" / trailing -> Handler.fromFunctionZIO[(Path, Request)] {
      case (path, _) =>
        toResponse(showResponse(path.toString))
    }
  )

  private def toResponse(thunk: => Response): ZIO[Any, Response, Response] = {
    ZIO
      .attempt(thunk)
      .catchAll(err =>
        ZIO.fail(Response.text(err.getMessage).status(Status.InternalServerError))
      )
  }

  private def listResponse(): Response = {
    val rows = DB.readOnly { case given DBSession =>
      SQL(
        "select stable_id, title, published_at, modified_at from blogs order by coalesce(published_at, modified_at) desc"
      )
        .map(ZioBlogListItem.from)
        .list
        .apply()
    }

    val body = rows
      .map { row =>
        val displayDate = summon[controllers.BlogDateTime].format(row.publishedAt.orElse(row.modifiedAt))
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

  private def showResponse(stableId: String): Response = {
    val row = DB.readOnly { case given DBSession =>
      SQL("select stable_id, title, body, published_at, modified_at from blogs where stable_id = ? limit 1")
        .bind(stableId)
        .map(ZioBlogShowItem.from)
        .single
        .apply()
    }

    row match {
      case None =>
        Response.text("Blog not found").status(Status.NotFound)
      case Some(blog) =>
        val displayDate = summon[controllers.BlogDateTime].format(blog.publishedAt.orElse(blog.modifiedAt))
        val title = if (blog.title.nonEmpty) escape(blog.title) else "(untitled)"
        val draft = if (blog.publishedAt.isEmpty) "<span class=\"draft\" title=\"Draft\">📝</span>" else ""
        htmlResponse(
          s"""
             |<div class="container">
             |  <a class="back" href="/">Back</a>
             |  <header class="header">
             |    <div class="title">${draft}${title}</div>
             |    <div class="subtitle">${escape(displayDate)}</div>
             |  </header>
             |  <section class="card post-body">
             |    ${blog.body}
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

  private def init(): Unit = {
    DBs.setupAll()
    SqlLogging.install()

    val classLoader = Thread.currentThread.getContextClassLoader

    val initSqlUrl =
      Option(classLoader.getResource("init.sql"))
        .getOrElse(throw RuntimeException("no such resource: init.sql"))
    DbInitializer.initFromResource(initSqlUrl)

    DB.localTx { case given DBSession =>
      val metaUrls =
        Using(ClassGraph().acceptPaths("blog").scan())(
          _.getAllResources
            .filter(_.getPath.endsWith("meta.yaml"))
            .asScala
            .map(_.getURL)
            .toSeq
        ).fold(throw _, identity)

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

      val themePath =
        Option(classLoader.getResource("tm4e/theme.json"))
          .getOrElse(throw RuntimeException("no such resource: tm4e/theme.json"))

      val renderer = MarkdownRendererImpl(Some(Tm4eHighlighter(grammars, themePath)))
      BlogImporter()
        .importAllEither(metaUrls, renderer)
        .fold(err => throw RuntimeException(err.toString), identity)
    }
  }

  override def run: Task[Unit] = {
    ZIO
      .attempt(init())
      .flatMap(_ => Server.serve(routes))
      .provide(Server.defaultWithPort(port))
      .ensuring(ZIO.attempt(DBs.closeAll()).ignore)
  }
}
