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
  DbInitializer.initFromFile(environment.getFile("conf/init.sql").toPath)
  BlogImporter
    .importAllEither(environment.getFile("conf/blog").toPath)
    .fold(err => throw new RuntimeException(err.toString), identity)
  applicationLifecycle.addStopHook { () =>
    Future.successful(DBs.closeAll())
  }

  lazy val homeController = new controllers.HomeController(controllerComponents)

  override def router: Router = new Routes(httpErrorHandler, homeController)
}
