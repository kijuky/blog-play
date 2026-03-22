package loader

import play.api.Application
import play.api.ApplicationLoader
import play.api.BuiltInComponentsFromContext
import play.api.LoggerConfigurator
import play.api.Logging
import play.api.routing.Router
import play.filters.HttpFiltersComponents
import router.Routes
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.config.DBs
import services.BlogImporter
import services.DbInitializer
import services.HttpOgpClient
import services.MarkdownRendererImpl
import services.SqlLogging
import services.Tm4eHighlighter

import java.time.ZoneId
import scala.concurrent.Future
import scala.io.Source
import scala.util.Failure
import scala.util.Success
import scala.util.Using

class AppLoader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): Application = {
    LoggerConfigurator(context.environment.classLoader)
      .foreach(_.configure(context.environment))
    MyComponents(context).application
  }
}

final class MyComponents(context: ApplicationLoader.Context)
    extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with controllers.AssetsComponents
    with Logging {
  private val ogpClient = HttpOgpClient()

  // Initialize ScalikeJDBC connection pools at startup
  DBs.setupAll()
  SqlLogging.install()
  DbInitializer.initFromResource(
    environment
      .resource("init.sql")
      .getOrElse(throw RuntimeException("no such resource: init.sql"))
  )
  private given zoneId: ZoneId = ZoneId.systemDefault
  DB.localTx { case given DBSession =>
    val metaURLs = {
      Using(Source.fromResource("blog.txt"))(
        _.getLines.map(getClass.getClassLoader.getResource).toSeq
      ).fold(throw _, identity)
    }
    val markdownRenderer = {
      val languages = {
        Using(Source.fromResource("tm4e-lang.txt"))(_.getLines.toSeq)
          .fold(throw _, identity)
      }
      val grammars = {
        languages.flatMap(l =>
          services.GrammarSpec.from(l) match {
            case Success(v) => Some(v)
            case Failure(e) =>
              logger.error(s"Failed to parse tm4e grammar: $l", e)
              None
          }
        )
      }
      val themePath =
        environment
          .resource("tm4e/theme.json")
          .getOrElse(throw RuntimeException("no such file: tm4e/theme.json"))
      val highlighter = Tm4eHighlighter(grammars, themePath)
      MarkdownRendererImpl(Some(highlighter))
    }
    BlogImporter()
      .importAllEither(metaURLs, markdownRenderer)
      .fold(err => throw RuntimeException(err.toString), identity)
  }
  applicationLifecycle.addStopHook { () => Future.successful(DBs.closeAll()) }

  // controllers

  private given controllers.BlogDateTime =
    controllers.BlogDateTime
      .from(zoneId, configuration.getOptional[String]("blog.datetime.format"))
  private lazy val blogListController =
    controllers.BlogListController(controllerComponents, messagesApi)
  private lazy val blogShowController =
    controllers.BlogShowController(controllerComponents, messagesApi)
  private lazy val ogpController =
    controllers.OgpController(controllerComponents, ogpClient)

  // router

  override def router: Router =
    Routes(
      httpErrorHandler,
      blogListController,
      blogShowController,
      ogpController,
      assets
    )
}
