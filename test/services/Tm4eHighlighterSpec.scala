package services

import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.Paths

class Tm4eHighlighterSpec extends AnyFunSuite {
  test("highlight returns html for scala code") {
    val grammarPath = Paths.get("conf/scala.tmLanguage.json")
    val javaGrammarPath = Paths.get("conf/java.tmLanguage.json")
    val themePath = Paths.get("conf/tm4e-theme.json")
    val highlighter = new Tm4eHighlighter(
      grammars = Seq(
        GrammarSpec(languageId = "scala", scopeName = "source.scala", grammarPath = grammarPath),
        GrammarSpec(languageId = "java", scopeName = "source.java", grammarPath = javaGrammarPath)
      ),
      themePath = themePath
    )

    val html = highlighter.highlight("val x = 1", Some("scala"))
    assert(html.nonEmpty)
  }

  test("highlight returns html for java code") {
    val scalaGrammarPath = Paths.get("conf/scala.tmLanguage.json")
    val javaGrammarPath = Paths.get("conf/java.tmLanguage.json")
    val themePath = Paths.get("conf/tm4e-theme.json")
    val highlighter = new Tm4eHighlighter(
      grammars = Seq(
        GrammarSpec(languageId = "scala", scopeName = "source.scala", grammarPath = scalaGrammarPath),
        GrammarSpec(languageId = "java", scopeName = "source.java", grammarPath = javaGrammarPath)
      ),
      themePath = themePath
    )

    val html = highlighter.highlight("public class Main {}", Some("java"))
    assert(html.nonEmpty)
  }
}
