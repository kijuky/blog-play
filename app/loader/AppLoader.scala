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
    val hoconGrammarPath = environment.getFile("conf/hocon.tmLanguage.json").toPath
    val dockerfileGrammarPath = environment.getFile("conf/dockerfile.tmLanguage.json").toPath
    val cGrammarPath = environment.getFile("conf/c.tmLanguage.json").toPath
    val cppGrammarPath = environment.getFile("conf/cpp.tmLanguage.json").toPath
    val objcGrammarPath = environment.getFile("conf/objective-c.tmLanguage.json").toPath
    val sqlGrammarPath = environment.getFile("conf/sql.tmLanguage.json").toPath
    val pythonGrammarPath = environment.getFile("conf/python.tmLanguage.json").toPath
    val kotlinGrammarPath = environment.getFile("conf/kotlin.tmLanguage.json").toPath
    val clojureGrammarPath = environment.getFile("conf/clojure.tmLanguage.json").toPath
    val swiftGrammarPath = environment.getFile("conf/swift.tmLanguage.json").toPath
    val rubyGrammarPath = environment.getFile("conf/ruby.tmLanguage.json").toPath
    val goGrammarPath = environment.getFile("conf/go.tmLanguage.json").toPath
    val hspGrammarPath = environment.getFile("conf/hsp.tmLanguage.json").toPath
    val nscripterGrammarPath = environment.getFile("conf/nscripter.tmLanguage.json").toPath
    val bnfGrammarPath = environment.getFile("conf/bnf.tmLanguage.json").toPath
    val propertiesGrammarPath = environment.getFile("conf/properties.tmLanguage.json").toPath
    val htmlGrammarPath = environment.getFile("conf/html.tmLanguage.json").toPath
    val gradleGrammarPath = environment.getFile("conf/gradle.tmLanguage.json").toPath
    val mustacheGrammarPath = environment.getFile("conf/mustache.tmLanguage.json").toPath
    val fortranGrammarPath = environment.getFile("conf/fortran.tmLanguage.json").toPath
    val mathGrammarPath = environment.getFile("conf/math.tmLanguage.json").toPath
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
        services.GrammarSpec(languageId = "js", scopeName = "source.js", grammarPath = javascriptGrammarPath),
        services.GrammarSpec(languageId = "hocon", scopeName = "source.hocon", grammarPath = hoconGrammarPath),
        services.GrammarSpec(languageId = "dockerfile", scopeName = "source.dockerfile", grammarPath = dockerfileGrammarPath),
        services.GrammarSpec(languageId = "Dockerfile", scopeName = "source.dockerfile", grammarPath = dockerfileGrammarPath),
        services.GrammarSpec(languageId = "c", scopeName = "source.c", grammarPath = cGrammarPath),
        services.GrammarSpec(languageId = "cpp", scopeName = "source.cpp", grammarPath = cppGrammarPath),
        services.GrammarSpec(languageId = "c++", scopeName = "source.cpp", grammarPath = cppGrammarPath),
        services.GrammarSpec(languageId = "objective-c", scopeName = "source.objc", grammarPath = objcGrammarPath),
        services.GrammarSpec(languageId = "objective_c", scopeName = "source.objc", grammarPath = objcGrammarPath),
        services.GrammarSpec(languageId = "sql", scopeName = "source.sql", grammarPath = sqlGrammarPath),
        services.GrammarSpec(languageId = "python", scopeName = "source.python", grammarPath = pythonGrammarPath),
        services.GrammarSpec(languageId = "kotlin", scopeName = "source.kotlin", grammarPath = kotlinGrammarPath),
        services.GrammarSpec(languageId = "clojure", scopeName = "source.clojure", grammarPath = clojureGrammarPath),
        services.GrammarSpec(languageId = "swift", scopeName = "source.swift", grammarPath = swiftGrammarPath),
        services.GrammarSpec(languageId = "ruby", scopeName = "source.ruby", grammarPath = rubyGrammarPath),
        services.GrammarSpec(languageId = "go", scopeName = "source.go", grammarPath = goGrammarPath),
        services.GrammarSpec(languageId = "hsp", scopeName = "source.hsp", grammarPath = hspGrammarPath),
        services.GrammarSpec(languageId = "nscripter", scopeName = "source.nscripter", grammarPath = nscripterGrammarPath),
        services.GrammarSpec(languageId = "bnf", scopeName = "source.bnf", grammarPath = bnfGrammarPath),
        services.GrammarSpec(languageId = "properties", scopeName = "source.properties", grammarPath = propertiesGrammarPath),
        services.GrammarSpec(languageId = "html", scopeName = "text.html", grammarPath = htmlGrammarPath),
        services.GrammarSpec(languageId = "gradle", scopeName = "source.gradle", grammarPath = gradleGrammarPath),
        services.GrammarSpec(languageId = "mustache", scopeName = "text.mustache", grammarPath = mustacheGrammarPath),
        services.GrammarSpec(languageId = "fortran", scopeName = "source.fortran", grammarPath = fortranGrammarPath),
        services.GrammarSpec(languageId = "math", scopeName = "text.math", grammarPath = mathGrammarPath)
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
