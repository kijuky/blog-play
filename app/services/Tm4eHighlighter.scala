package services

import com.typesafe.config.ConfigFactory
import org.eclipse.tm4e.core.registry.Registry
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IRegistryOptions
import org.eclipse.tm4e.core.grammar.IStateStack

import java.nio.file.Path
import java.time.Duration
import scala.jdk.CollectionConverters.*

final class Tm4eHighlighter(
    grammarPath: Path,
    scopeName: String,
    themePath: Path,
    languageId: String
) extends CodeHighlighter {
  private val registry = new Registry(new IRegistryOptions {
    override def getGrammarSource(scope: String): IGrammarSource =
      if (scope == scopeName) IGrammarSource.fromFile(grammarPath) else null
  })

  private val grammar = {
    registry.loadGrammar(scopeName)
  }

  private val theme = TextMateTheme.fromFile(themePath)

  override def highlight(code: String, language: Option[String]): Option[String] = {
    if (language.exists(_ != languageId)) {
      None
    } else {
      val lines = code.split("\n", -1).toSeq
      val builder = new StringBuilder
      var ruleStack: Option[IStateStack] = None
      lines.foreach { line =>
        val result = ruleStack match {
          case Some(stack) => grammar.tokenizeLine(line, stack, Duration.ofSeconds(5))
          case None => grammar.tokenizeLine(line)
        }
        val tokens = result.getTokens.toSeq
        tokens.foreach { token =>
          val text = safeSlice(line, token.getStartIndex, token.getEndIndex)
          val scopes = token.getScopes.asScala.toSeq
          val style = theme.styleFor(scopes)
          builder.append(style.wrap(escapeHtml(text)))
        }
        builder.append("\n")
        ruleStack = Some(result.getRuleStack)
      }
      Some(builder.result())
    }
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

private final case class TextMateStyle(foreground: Option[String], fontStyle: Set[String]) {
  def wrap(content: String): String = {
    val styles = Seq(
      foreground.map(color => s"color: $color"),
      if (fontStyle.contains("bold")) Some("font-weight: 600") else None,
      if (fontStyle.contains("italic")) Some("font-style: italic") else None
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
      case None => TextMateStyle(None, Set.empty)
    }
  }
}

private object TextMateTheme {
  def fromFile(path: Path): TextMateTheme = {
    val config = ConfigFactory.parseFile(path.toFile)
    val tokenColors = config.getConfigList("tokenColors").asScala.toSeq
    val rules = tokenColors.map { entry =>
      val scopes =
        if (entry.getValue("scope").valueType().name() == "LIST") {
          entry.getStringList("scope").asScala.toSeq
        } else {
          entry.getString("scope").split(",").map(_.trim).filter(_.nonEmpty).toSeq
        }
      val settings = entry.getConfig("settings")
      val foreground = if (settings.hasPath("foreground")) Some(settings.getString("foreground")) else None
      val fontStyle = if (settings.hasPath("fontStyle")) {
        settings.getString("fontStyle").split(" ").map(_.trim).filter(_.nonEmpty).toSet
      } else {
        Set.empty[String]
      }
      TextMateThemeRule(scopes, foreground, fontStyle)
    }
    TextMateTheme(rules)
  }
}
