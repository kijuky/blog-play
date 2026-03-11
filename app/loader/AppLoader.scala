package loader

import play.api.Application
import play.api.ApplicationLoader
import play.api.BuiltInComponentsFromContext
import play.api.LoggerConfigurator
import play.api.routing.Router
import play.filters.HttpFiltersComponents
import router.Routes
import scalikejdbc.config.DBs
import services.BlogImporter
import services.DbInitializer
import services.MarkdownRenderer
import services.SqlLogging
import services.Tm4eHighlighter

import scala.concurrent.Future

class AppLoader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): Application = {
    LoggerConfigurator(context.environment.classLoader).foreach(_.configure(context.environment))
    new MyComponents(context).application
  }
}

final class MyComponents(context: ApplicationLoader.Context)
    extends BuiltInComponentsFromContext(context)
    with HttpFiltersComponents
    with controllers.AssetsComponents {

  // Initialize ScalikeJDBC connection pools at startup
  DBs.setupAll()
  SqlLogging.install()
  private val blogRoot = environment.getFile("conf/blog").toPath
  DbInitializer.initFromFile(environment.getFile("conf/init.sql").toPath)
  private val markdownRenderer = {
    val grammarPath = environment.getFile("conf/scala.tmLanguage.json").toPath
    val themePath = environment.getFile("conf/tm4e-theme.json").toPath
    val highlighter = new Tm4eHighlighter(
      grammarPath = grammarPath,
      scopeName = "source.scala",
      themePath = themePath,
      languageId = "scala"
    )
    new MarkdownRenderer(blogRoot, Some(highlighter))
  }

  BlogImporter
    .importAllEither(blogRoot, markdownRenderer)
    .fold(err => throw new RuntimeException(err.toString), identity)
  applicationLifecycle.addStopHook { () =>
    Future.successful(DBs.closeAll())
  }

  private given controllers.BlogDateTime =
    controllers.BlogDateTime
      .from(
        sys.env.get("TZ"),
        configuration.getOptional[String]("blog.datetime.format")
      )

  private lazy val blogListController = new controllers.BlogListController(controllerComponents)
  private lazy val blogShowController = new controllers.BlogShowController(controllerComponents)

  override def router: Router = new Routes(httpErrorHandler, blogListController, blogShowController, assets)
}
