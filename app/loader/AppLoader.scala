package loader

import play.api.Application
import play.api.ApplicationLoader
import play.api.BuiltInComponentsFromContext
import play.api.LoggerConfigurator
import play.api.routing.Router
import play.filters.HttpFiltersComponents
import scalikejdbc.config.DBs
import services.DbInitializer
import services.BlogImporter
import services.SqlLogging
import router.Routes

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
  BlogImporter
    .importAllEither(blogRoot)
    .fold(err => throw new RuntimeException(err.toString), identity)
  applicationLifecycle.addStopHook { () =>
    Future.successful(DBs.closeAll())
  }

  private lazy val blogListController = new controllers.BlogListController(controllerComponents)
  private lazy val blogShowController = new controllers.BlogShowController(controllerComponents)

  override def router: Router = new Routes(httpErrorHandler, blogListController, blogShowController, assets)
}
