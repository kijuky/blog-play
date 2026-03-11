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
    val scalaGrammarPath = environment.getFile("conf/scala.tmLanguage.json").toPath
    val javaGrammarPath = environment.getFile("conf/java.tmLanguage.json").toPath
    val shellGrammarPath = environment.getFile("conf/shell.tmLanguage.json").toPath
    val bashGrammarPath = environment.getFile("conf/bash.tmLanguage.json").toPath
    val zshGrammarPath = environment.getFile("conf/zsh.tmLanguage.json").toPath
    val cmdGrammarPath = environment.getFile("conf/cmd.tmLanguage.json").toPath
    val consoleGrammarPath = environment.getFile("conf/console.tmLanguage.json").toPath
    val jsonGrammarPath = environment.getFile("conf/json.tmLanguage.json").toPath
    val xmlGrammarPath = environment.getFile("conf/xml.tmLanguage.json").toPath
    val yamlGrammarPath = environment.getFile("conf/yaml.tmLanguage.json").toPath
    val javascriptGrammarPath = environment.getFile("conf/javascript.tmLanguage.json").toPath
    val themePath = environment.getFile("conf/tm4e-theme.json").toPath
    val highlighter = new Tm4eHighlighter(
      grammars = Seq(
        services.GrammarSpec(languageId = "scala", scopeName = "source.scala", grammarPath = scalaGrammarPath),
        services.GrammarSpec(languageId = "java", scopeName = "source.java", grammarPath = javaGrammarPath),
        services.GrammarSpec(languageId = "shell", scopeName = "source.shell", grammarPath = shellGrammarPath),
        services.GrammarSpec(languageId = "sh", scopeName = "source.shell", grammarPath = shellGrammarPath),
        services.GrammarSpec(languageId = "bash", scopeName = "source.bash", grammarPath = bashGrammarPath),
        services.GrammarSpec(languageId = "zsh", scopeName = "source.zsh", grammarPath = zshGrammarPath),
        services.GrammarSpec(languageId = "console", scopeName = "source.console", grammarPath = consoleGrammarPath),
        services.GrammarSpec(languageId = "terminal", scopeName = "source.console", grammarPath = consoleGrammarPath),
        services.GrammarSpec(languageId = "cmd", scopeName = "source.cmd", grammarPath = cmdGrammarPath),
        services.GrammarSpec(languageId = "bat", scopeName = "source.cmd", grammarPath = cmdGrammarPath),
        services.GrammarSpec(languageId = "json", scopeName = "source.json", grammarPath = jsonGrammarPath),
        services.GrammarSpec(languageId = "xml", scopeName = "text.xml", grammarPath = xmlGrammarPath),
        services.GrammarSpec(languageId = "yaml", scopeName = "source.yaml", grammarPath = yamlGrammarPath),
        services.GrammarSpec(languageId = "yml", scopeName = "source.yaml", grammarPath = yamlGrammarPath),
        services.GrammarSpec(languageId = "javascript", scopeName = "source.js", grammarPath = javascriptGrammarPath),
        services.GrammarSpec(languageId = "js", scopeName = "source.js", grammarPath = javascriptGrammarPath)
      ),
      themePath = themePath
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
