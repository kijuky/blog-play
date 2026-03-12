package services

import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FilenameUtils
import org.eclipse.tm4e.core.grammar.IStateStack
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IRegistryOptions
import org.eclipse.tm4e.core.registry.Registry
import org.slf4j.LoggerFactory

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import scala.jdk.CollectionConverters.*
import scala.util.Try
import scala.util.Using

final case class GrammarSpec(
  languageId: String,
  scopeName: String,
  grammarPath: Path
)

object GrammarSpec {
  private val logger = LoggerFactory.getLogger(getClass)

  def from(basePath: Path): Seq[Try[GrammarSpec]] = {
    Using(Files.walk(basePath)) { paths =>
      paths.toList.asScala.flatMap { path =>
        val pathString = path.toString
        val ext = FilenameUtils.getExtension(pathString)
        Option.when(ext == "json") {
          val languageId = FilenameUtils.getBaseName(pathString)
          from(languageId, basePath)
        }
      }.toSeq
    }.getOrElse(Nil)
  }

  def from(languageId: String, basePath: Path): Try[GrammarSpec] =
    Try {
      val grammarPath = basePath.resolve(Path.of(s"$languageId.json"))
      val config = ConfigFactory.parseFile(grammarPath.toFile)
      val scopeName = config.getString("scopeName")
      val exts = config.getStringList("fileTypes").asScala.mkString(",")
      logger.info(
        s"supported $languageId highlight ($exts) from file:$grammarPath"
      )
      apply(languageId, scopeName, grammarPath)
    }
}

final class Tm4eHighlighter(grammars: Seq[GrammarSpec], themePath: Path)
    extends CodeHighlighter {
  private val scopeToPath =
    grammars.map(spec => spec.scopeName -> spec.grammarPath).toMap
  private val extensionToLanguage = buildExtensionMap(grammars)
  private val registry =
    Registry(new IRegistryOptions {
      override def getGrammarSource(scope: String): IGrammarSource =
        scopeToPath.get(scope).map(IGrammarSource.fromFile).orNull
    })

  private val grammarByLanguage =
    grammars.flatMap { spec =>
      Option(registry.loadGrammar(spec.scopeName)).map(spec.languageId -> _)
    }.toMap

  private val theme = TextMateTheme.fromFile(themePath)

  override def highlight(
    code: String,
    language: Option[String]
  ): Option[String] = {
    val grammar = language.flatMap(grammarByLanguage.get)
    grammar.map { selectedGrammar =>
      val lines = code.split("\n", -1).toSeq
      val builder = StringBuilder()
      var ruleStack: Option[IStateStack] = None
      lines.zipWithIndex.foreach { (line, index) =>
        val result =
          ruleStack match {
            case Some(stack) =>
              selectedGrammar.tokenizeLine(line, stack, Duration.ofSeconds(5))
            case None =>
              selectedGrammar.tokenizeLine(line)
          }
        val tokens = result.getTokens.toSeq
        tokens.foreach { token =>
          val text = safeSlice(line, token.getStartIndex, token.getEndIndex)
          val scopes = token.getScopes.asScala.toSeq
          val style = theme.styleFor(scopes)
          builder.append(style.wrap(escapeHtml(text)))
        }
        if (index < lines.length - 1) {
          builder.append("\n")
        }
        ruleStack = Some(result.getRuleStack)
      }
      builder.result()
    }
  }

  override def languageForExtension(ext: String): Option[String] = {
    val normalized = ext.toLowerCase.stripPrefix(".")
    extensionToLanguage.get(normalized)
  }

  private def escapeHtml(value: String): String = {
    value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
  }

  private def safeSlice(line: String, start: Int, end: Int): String = {
    val safeStart = Math.max(0, Math.min(start, line.length))
    val safeEnd = Math.max(safeStart, Math.min(end, line.length))
    line.substring(safeStart, safeEnd)
  }
}

private final case class TextMateThemeRule(
  scopes: Seq[String],
  foreground: Option[String],
  fontStyle: Set[String]
) {
  def matches(scope: String): Boolean =
    scopes.exists(s => scope == s || scope.startsWith(s + "."))
}

private final case class TextMateStyle(
  foreground: Option[String],
  fontStyle: Set[String]
) {
  def wrap(content: String): String = {
    val styles =
      Seq(
        foreground.map("color: " + _),
        Option.when(fontStyle.contains("bold"))("font-weight: 600"),
        Option.when(fontStyle.contains("italic"))("font-style: italic")
      ).flatten

    if (styles.isEmpty) content
    else s"""<span style="${styles.mkString("; ")}">$content</span>"""
  }
}

private final class TextMateTheme(rules: Seq[TextMateThemeRule]) {
  def styleFor(scopes: Seq[String]): TextMateStyle = {
    val matched = scopes.flatMap(scope => rules.find(_.matches(scope)))
    matched.headOption match {
      case Some(rule) => TextMateStyle(rule.foreground, rule.fontStyle)
      case None       => TextMateStyle(None, Set.empty)
    }
  }
}

private object TextMateTheme {
  def fromFile(path: Path): TextMateTheme = {
    val config = ConfigFactory.parseFile(path.toFile)
    val tokenColors = config.getConfigList("tokenColors").asScala.toSeq
    val rules =
      tokenColors.map { entry =>
        val scopes =
          if (entry.getValue("scope").valueType.name == "LIST") {
            entry.getStringList("scope").asScala.toSeq
          } else {
            entry
              .getString("scope")
              .split(",")
              .map(_.trim)
              .filter(_.nonEmpty)
              .toSeq
          }
        val settings = entry.getConfig("settings")
        val foreground =
          Option.when(settings.hasPath("foreground"))(
            settings.getString("foreground")
          )
        val fontStyle: Set[String] =
          if (settings.hasPath("fontStyle")) {
            settings
              .getString("fontStyle")
              .split(" ")
              .map(_.trim)
              .filter(_.nonEmpty)
              .toSet
          } else {
            Set.empty
          }
        TextMateThemeRule(scopes, foreground, fontStyle)
      }
    TextMateTheme(rules)
  }
}

private def buildExtensionMap(
  grammars: Seq[GrammarSpec]
): Map[String, String] = {
  grammars.foldLeft[Map[String, String]](Map.empty) { (acc, spec) =>
    val config = ConfigFactory.parseFile(spec.grammarPath.toFile)
    val extensions =
      if (config.hasPath("fileTypes")) {
        config.getStringList("fileTypes").asScala.toSeq
      } else {
        Nil
      }
    extensions.foldLeft(acc) { (map, ext) =>
      val normalized = ext.toLowerCase.stripPrefix(".")
      if (normalized.isEmpty || map.contains(normalized)) map
      else map.updated(normalized, spec.languageId)
    }
  }
}
