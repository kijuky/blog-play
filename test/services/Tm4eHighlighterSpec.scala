package services

import org.scalatest.funsuite.AnyFunSuite

import java.nio.file.Paths

class Tm4eHighlighterSpec extends AnyFunSuite {
  test("highlight returns html for scala code") {
    val grammarPath = Paths.get("conf/scala.tmLanguage.json")
    val themePath = Paths.get("conf/tm4e-theme.json")
    val highlighter = new Tm4eHighlighter(
      grammarPath = grammarPath,
      scopeName = "source.scala",
      themePath = themePath,
      languageId = "scala"
    )

    val html = highlighter.highlight("val x = 1", Some("scala"))
    assert(html.nonEmpty)
  }
}
