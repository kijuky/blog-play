package loader

import io.github.classgraph.ClassGraph
import play.api.Application
import play.api.ApplicationLoader
import play.api.BuiltInComponentsFromContext
import play.api.LoggerConfigurator
import play.api.routing.Router
import play.filters.HttpFiltersComponents
import router.Routes
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.config.DBs
import services.BlogImporter
import services.DbInitializer
import services.MarkdownRendererImpl
import services.SqlLogging
import services.Tm4eHighlighter

import java.time.ZoneId
import scala.concurrent.Future
import scala.jdk.CollectionConverters.*
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
    with controllers.AssetsComponents {

  // Initialize ScalikeJDBC connection pools at startup
  DBs.setupAll()
  SqlLogging.install()
  DbInitializer.initFromFile(environment.getFile("conf/init.sql").toPath)
  private lazy val markdownRenderer = {
    val grammars =
      services.GrammarSpec
        .from(environment.getFile("conf/tm4e/lang").toPath)
        .flatMap(_.toOption)
    val themePath = environment.getFile("conf/tm4e/theme.json").toPath
    val highlighter = Tm4eHighlighter(grammars, themePath)
    MarkdownRendererImpl(Some(highlighter))
  }
  private given zoneId: ZoneId = ZoneId.systemDefault
  DB.localTx { case given DBSession =>
    val metaURLs = {
      Using(ClassGraph().acceptPaths("blog").scan())(
        _.getAllResources
          .filter(_.getPath.endsWith("meta.yaml"))
          .asScala
          .map(_.getURL)
          .toSeq
      ).getOrElse(Nil)
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
    controllers.BlogListController(controllerComponents)
  private lazy val blogShowController =
    controllers.BlogShowController(controllerComponents)

  // router

  override def router: Router =
    Routes(httpErrorHandler, blogListController, blogShowController, assets)
}
