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
    val shellGrammarPath = Paths.get("conf/shell.tmLanguage.json")
    val themePath = Paths.get("conf/tm4e-theme.json")
    val highlighter = new Tm4eHighlighter(
      grammars = Seq(
        GrammarSpec(languageId = "scala", scopeName = "source.scala", grammarPath = scalaGrammarPath),
        GrammarSpec(languageId = "java", scopeName = "source.java", grammarPath = javaGrammarPath),
        GrammarSpec(languageId = "shell", scopeName = "source.shell", grammarPath = shellGrammarPath)
      ),
      themePath = themePath
    )

    val html = highlighter.highlight("public class Main {}", Some("java"))
    assert(html.nonEmpty)
  }

  test("highlight returns html for shell code") {
    val scalaGrammarPath = Paths.get("conf/scala.tmLanguage.json")
    val javaGrammarPath = Paths.get("conf/java.tmLanguage.json")
    val shellGrammarPath = Paths.get("conf/shell.tmLanguage.json")
    val bashGrammarPath = Paths.get("conf/bash.tmLanguage.json")
    val zshGrammarPath = Paths.get("conf/zsh.tmLanguage.json")
    val cmdGrammarPath = Paths.get("conf/cmd.tmLanguage.json")
    val consoleGrammarPath = Paths.get("conf/console.tmLanguage.json")
    val themePath = Paths.get("conf/tm4e-theme.json")
    val highlighter = new Tm4eHighlighter(
      grammars = Seq(
        GrammarSpec(languageId = "scala", scopeName = "source.scala", grammarPath = scalaGrammarPath),
        GrammarSpec(languageId = "java", scopeName = "source.java", grammarPath = javaGrammarPath),
        GrammarSpec(languageId = "shell", scopeName = "source.shell", grammarPath = shellGrammarPath),
        GrammarSpec(languageId = "bash", scopeName = "source.bash", grammarPath = bashGrammarPath),
        GrammarSpec(languageId = "zsh", scopeName = "source.zsh", grammarPath = zshGrammarPath),
        GrammarSpec(languageId = "cmd", scopeName = "source.cmd", grammarPath = cmdGrammarPath),
        GrammarSpec(languageId = "console", scopeName = "source.console", grammarPath = consoleGrammarPath)
      ),
      themePath = themePath
    )

    val html = highlighter.highlight("echo \"hi\"", Some("shell"))
    assert(html.nonEmpty)
  }

  test("highlight returns html for bash, zsh, cmd, and console") {
    val scalaGrammarPath = Paths.get("conf/scala.tmLanguage.json")
    val javaGrammarPath = Paths.get("conf/java.tmLanguage.json")
    val shellGrammarPath = Paths.get("conf/shell.tmLanguage.json")
    val bashGrammarPath = Paths.get("conf/bash.tmLanguage.json")
    val zshGrammarPath = Paths.get("conf/zsh.tmLanguage.json")
    val cmdGrammarPath = Paths.get("conf/cmd.tmLanguage.json")
    val consoleGrammarPath = Paths.get("conf/console.tmLanguage.json")
    val themePath = Paths.get("conf/tm4e-theme.json")
    val highlighter = new Tm4eHighlighter(
      grammars = Seq(
        GrammarSpec(languageId = "scala", scopeName = "source.scala", grammarPath = scalaGrammarPath),
        GrammarSpec(languageId = "java", scopeName = "source.java", grammarPath = javaGrammarPath),
        GrammarSpec(languageId = "shell", scopeName = "source.shell", grammarPath = shellGrammarPath),
        GrammarSpec(languageId = "bash", scopeName = "source.bash", grammarPath = bashGrammarPath),
        GrammarSpec(languageId = "zsh", scopeName = "source.zsh", grammarPath = zshGrammarPath),
        GrammarSpec(languageId = "cmd", scopeName = "source.cmd", grammarPath = cmdGrammarPath),
        GrammarSpec(languageId = "console", scopeName = "source.console", grammarPath = consoleGrammarPath)
      ),
      themePath = themePath
    )

    assert(highlighter.highlight("echo \"hi\"", Some("bash")).nonEmpty)
    assert(highlighter.highlight("echo \"hi\"", Some("zsh")).nonEmpty)
    assert(highlighter.highlight("echo hi", Some("cmd")).nonEmpty)
    assert(highlighter.highlight("$ ls -la", Some("console")).nonEmpty)
  }
}
