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
    val jsonGrammarPath = Paths.get("conf/json.tmLanguage.json")
    val xmlGrammarPath = Paths.get("conf/xml.tmLanguage.json")
    val yamlGrammarPath = Paths.get("conf/yaml.tmLanguage.json")
    val javascriptGrammarPath = Paths.get("conf/javascript.tmLanguage.json")
    val hoconGrammarPath = Paths.get("conf/hocon.tmLanguage.json")
    val dockerfileGrammarPath = Paths.get("conf/dockerfile.tmLanguage.json")
    val cGrammarPath = Paths.get("conf/c.tmLanguage.json")
    val cppGrammarPath = Paths.get("conf/cpp.tmLanguage.json")
    val objcGrammarPath = Paths.get("conf/objective-c.tmLanguage.json")
    val themePath = Paths.get("conf/tm4e-theme.json")
    val highlighter = new Tm4eHighlighter(
      grammars = Seq(
        GrammarSpec(languageId = "scala", scopeName = "source.scala", grammarPath = scalaGrammarPath),
        GrammarSpec(languageId = "java", scopeName = "source.java", grammarPath = javaGrammarPath),
        GrammarSpec(languageId = "shell", scopeName = "source.shell", grammarPath = shellGrammarPath),
        GrammarSpec(languageId = "bash", scopeName = "source.bash", grammarPath = bashGrammarPath),
        GrammarSpec(languageId = "zsh", scopeName = "source.zsh", grammarPath = zshGrammarPath),
        GrammarSpec(languageId = "cmd", scopeName = "source.cmd", grammarPath = cmdGrammarPath),
        GrammarSpec(languageId = "console", scopeName = "source.console", grammarPath = consoleGrammarPath),
        GrammarSpec(languageId = "json", scopeName = "source.json", grammarPath = jsonGrammarPath),
        GrammarSpec(languageId = "xml", scopeName = "text.xml", grammarPath = xmlGrammarPath),
        GrammarSpec(languageId = "yaml", scopeName = "source.yaml", grammarPath = yamlGrammarPath),
        GrammarSpec(languageId = "javascript", scopeName = "source.js", grammarPath = javascriptGrammarPath)
        ,
        GrammarSpec(languageId = "hocon", scopeName = "source.hocon", grammarPath = hoconGrammarPath),
        GrammarSpec(languageId = "dockerfile", scopeName = "source.dockerfile", grammarPath = dockerfileGrammarPath),
        GrammarSpec(languageId = "c", scopeName = "source.c", grammarPath = cGrammarPath),
        GrammarSpec(languageId = "cpp", scopeName = "source.cpp", grammarPath = cppGrammarPath),
        GrammarSpec(languageId = "objective-c", scopeName = "source.objc", grammarPath = objcGrammarPath)
      ),
      themePath = themePath
    )

    assert(highlighter.highlight("echo \"hi\"", Some("bash")).nonEmpty)
    assert(highlighter.highlight("echo \"hi\"", Some("zsh")).nonEmpty)
    assert(highlighter.highlight("echo hi", Some("cmd")).nonEmpty)
    assert(highlighter.highlight("$ ls -la", Some("console")).nonEmpty)
    assert(highlighter.highlight("{\"name\": \"ok\"}", Some("json")).nonEmpty)
    assert(highlighter.highlight("<root></root>", Some("xml")).nonEmpty)
    assert(highlighter.highlight("name: ok", Some("yaml")).nonEmpty)
    assert(highlighter.highlight("const x = 1", Some("javascript")).nonEmpty)
    assert(highlighter.highlight("app.name = \"blog\"", Some("hocon")).nonEmpty)
    assert(highlighter.highlight("FROM alpine:3.19", Some("dockerfile")).nonEmpty)
    assert(highlighter.highlight("int main() { return 0; }", Some("c")).nonEmpty)
    assert(highlighter.highlight("std::string s;", Some("cpp")).nonEmpty)
    assert(highlighter.highlight("@interface Hoge : NSObject @end", Some("objective-c")).nonEmpty)
  }
}
