package loader

import play.api.Application
import play.api.ApplicationLoader
import play.api.BuiltInComponentsFromContext
import play.api.LoggerConfigurator
import play.api.NoHttpFiltersComponents
import play.api.routing.Router
import scala.concurrent.Future
import scalikejdbc.config.DBs
import services.DbInitializer
import services.BlogImporter
import router.Routes

class AppLoader extends ApplicationLoader {
  override def load(context: ApplicationLoader.Context): Application = {
    LoggerConfigurator(context.environment.classLoader).foreach(_.configure(context.environment))
    new MyComponents(context).application
  }
}

final class MyComponents(context: ApplicationLoader.Context)
    extends BuiltInComponentsFromContext(context)
    with NoHttpFiltersComponents {

  // Initialize ScalikeJDBC connection pools at startup
  DBs.setupAll()
  private val blogRoot = environment.getFile("conf/blog").toPath
  DbInitializer.initFromFile(environment.getFile("conf/init.sql").toPath)
  BlogImporter
    .importAllEither(blogRoot)
    .fold(err => throw new RuntimeException(err.toString), identity)
  applicationLifecycle.addStopHook { () =>
    Future.successful(DBs.closeAll())
  }

  lazy val blogListController = new controllers.BlogListController(controllerComponents)
  lazy val blogShowController = new controllers.BlogShowController(controllerComponents)

  override def router: Router = new Routes(httpErrorHandler, blogListController, blogShowController)
}
