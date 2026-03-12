package services

import org.scalatest.funsuite.AnyFunSuite

import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths

class Tm4eHighlighterSpec extends AnyFunSuite {
  test("highlight returns html for scala code") {
    val grammarPath = resourcePath("lang/scala.json")
    val javaGrammarPath = resourcePath("lang/java.json")
    val themePath = resourceUrl("theme.json")
    val sut =
      Tm4eHighlighter(
        Seq(
          GrammarSpec("scala", "source.scala", grammarPath),
          GrammarSpec("java", "source.java", javaGrammarPath)
        ),
        themePath
      )

    val actual = sut.highlight("val x = 1", Some("scala"))
    assert(actual.nonEmpty)
  }

  test("highlight returns html for java code") {
    val scalaGrammarPath = resourcePath("lang/scala.json")
    val javaGrammarPath = resourcePath("lang/java.json")
    val shellGrammarPath = resourcePath("lang/shell.json")
    val themePath = resourceUrl("theme.json")
    val sut =
      Tm4eHighlighter(
        Seq(
          GrammarSpec("scala", "source.scala", scalaGrammarPath),
          GrammarSpec("java", "source.java", javaGrammarPath),
          GrammarSpec("shell", "source.shell", shellGrammarPath)
        ),
        themePath
      )

    val actual = sut.highlight("public class Main {}", Some("java"))
    assert(actual.nonEmpty)
  }

  test("highlight returns html for shell code") {
    val scalaGrammarPath = resourcePath("lang/scala.json")
    val javaGrammarPath = resourcePath("lang/java.json")
    val shellGrammarPath = resourcePath("lang/shell.json")
    val bashGrammarPath = resourcePath("lang/bash.json")
    val zshGrammarPath = resourcePath("lang/zsh.json")
    val cmdGrammarPath = resourcePath("lang/cmd.json")
    val consoleGrammarPath = resourcePath("lang/console.json")
    val themePath = resourceUrl("theme.json")
    val sut =
      Tm4eHighlighter(
        Seq(
          GrammarSpec("scala", "source.scala", scalaGrammarPath),
          GrammarSpec("java", "source.java", javaGrammarPath),
          GrammarSpec("shell", "source.shell", shellGrammarPath),
          GrammarSpec("bash", "source.bash", bashGrammarPath),
          GrammarSpec("zsh", "source.zsh", zshGrammarPath),
          GrammarSpec("cmd", "source.cmd", cmdGrammarPath),
          GrammarSpec("console", "source.console", consoleGrammarPath)
        ),
        themePath
      )

    val actual = sut.highlight("echo \"hi\"", Some("shell"))
    assert(actual.nonEmpty)
  }

  test("highlight returns html for bash, zsh, cmd, and console") {
    val scalaGrammarPath = resourcePath("lang/scala.json")
    val javaGrammarPath = resourcePath("lang/java.json")
    val shellGrammarPath = resourcePath("lang/shell.json")
    val bashGrammarPath = resourcePath("lang/bash.json")
    val zshGrammarPath = resourcePath("lang/zsh.json")
    val cmdGrammarPath = resourcePath("lang/cmd.json")
    val consoleGrammarPath = resourcePath("lang/console.json")
    val jsonGrammarPath = resourcePath("lang/json.json")
    val xmlGrammarPath = resourcePath("lang/xml.json")
    val yamlGrammarPath = resourcePath("lang/yaml.json")
    val javascriptGrammarPath = resourcePath("lang/javascript.json")
    val hoconGrammarPath = resourcePath("lang/hocon.json")
    val dockerfileGrammarPath = resourcePath("lang/dockerfile.json")
    val cGrammarPath = resourcePath("lang/c.json")
    val cppGrammarPath = resourcePath("lang/cpp.json")
    val objcGrammarPath = resourcePath("lang/objective-c.json")
    val sqlGrammarPath = resourcePath("lang/sql.json")
    val pythonGrammarPath = resourcePath("lang/python.json")
    val kotlinGrammarPath = resourcePath("lang/kotlin.json")
    val clojureGrammarPath = resourcePath("lang/clojure.json")
    val swiftGrammarPath = resourcePath("lang/swift.json")
    val rubyGrammarPath = resourcePath("lang/ruby.json")
    val goGrammarPath = resourcePath("lang/go.json")
    val hspGrammarPath = resourcePath("lang/hsp.json")
    val nscripterGrammarPath = resourcePath("lang/nscripter.json")
    val bnfGrammarPath = resourcePath("lang/bnf.json")
    val propertiesGrammarPath = resourcePath("lang/properties.json")
    val htmlGrammarPath = resourcePath("lang/html.json")
    val gradleGrammarPath = resourcePath("lang/gradle.json")
    val mustacheGrammarPath = resourcePath("lang/mustache.json")
    val fortranGrammarPath = resourcePath("lang/fortran.json")
    val mathGrammarPath = resourcePath("lang/math.json")
    val themePath = resourceUrl("theme.json")
    val sut =
      Tm4eHighlighter(
        Seq(
          GrammarSpec("scala", "source.scala", scalaGrammarPath),
          GrammarSpec("java", "source.java", javaGrammarPath),
          GrammarSpec("shell", "source.shell", shellGrammarPath),
          GrammarSpec("bash", "source.bash", bashGrammarPath),
          GrammarSpec("zsh", "source.zsh", zshGrammarPath),
          GrammarSpec("cmd", "source.cmd", cmdGrammarPath),
          GrammarSpec("console", "source.console", consoleGrammarPath),
          GrammarSpec("json", "source.json", jsonGrammarPath),
          GrammarSpec("xml", "text.xml", xmlGrammarPath),
          GrammarSpec("yaml", "source.yaml", yamlGrammarPath),
          GrammarSpec("javascript", "source.js", javascriptGrammarPath),
          GrammarSpec("hocon", "source.hocon", hoconGrammarPath),
          GrammarSpec("dockerfile", "source.dockerfile", dockerfileGrammarPath),
          GrammarSpec("c", "source.c", cGrammarPath),
          GrammarSpec("cpp", "source.cpp", cppGrammarPath),
          GrammarSpec("objective-c", "source.objc", objcGrammarPath),
          GrammarSpec("sql", "source.sql", sqlGrammarPath),
          GrammarSpec("python", "source.python", pythonGrammarPath),
          GrammarSpec("kotlin", "source.kotlin", kotlinGrammarPath),
          GrammarSpec("clojure", "source.clojure", clojureGrammarPath),
          GrammarSpec("swift", "source.swift", swiftGrammarPath),
          GrammarSpec("ruby", "source.ruby", rubyGrammarPath),
          GrammarSpec("go", "source.go", goGrammarPath),
          GrammarSpec("hsp", "source.hsp", hspGrammarPath),
          GrammarSpec("nscripter", "source.nscripter", nscripterGrammarPath),
          GrammarSpec("bnf", "source.bnf", bnfGrammarPath),
          GrammarSpec("properties", "source.properties", propertiesGrammarPath),
          GrammarSpec("html", "text.html", htmlGrammarPath),
          GrammarSpec("gradle", "source.gradle", gradleGrammarPath),
          GrammarSpec("mustache", "text.mustache", mustacheGrammarPath),
          GrammarSpec("fortran", "source.fortran", fortranGrammarPath),
          GrammarSpec("math", "text.math", mathGrammarPath)
        ),
        themePath
      )

    assert(sut.highlight("echo \"hi\"", Some("bash")).nonEmpty)
    assert(sut.highlight("echo \"hi\"", Some("zsh")).nonEmpty)
    assert(sut.highlight("echo hi", Some("cmd")).nonEmpty)
    assert(sut.highlight("$ ls -la", Some("console")).nonEmpty)
    assert(sut.highlight("{\"name\": \"ok\"}", Some("json")).nonEmpty)
    assert(sut.highlight("<root></root>", Some("xml")).nonEmpty)
    assert(sut.highlight("name: ok", Some("yaml")).nonEmpty)
    assert(sut.highlight("const x = 1", Some("javascript")).nonEmpty)
    assert(sut.highlight("app.name = \"blog\"", Some("hocon")).nonEmpty)
    assert(sut.highlight("FROM alpine:3.19", Some("dockerfile")).nonEmpty)
    assert(sut.highlight("int main() { return 0; }", Some("c")).nonEmpty)
    assert(sut.highlight("std::string s;", Some("cpp")).nonEmpty)
    assert(
      sut
        .highlight("@interface Hoge : NSObject @end", Some("objective-c"))
        .nonEmpty
    )
    assert(sut.highlight("select * from blogs;", Some("sql")).nonEmpty)
    assert(sut.highlight("def main(): pass", Some("python")).nonEmpty)
    assert(sut.highlight("fun main() {}", Some("kotlin")).nonEmpty)
    assert(sut.highlight("(defn f [] 1)", Some("clojure")).nonEmpty)
    assert(sut.highlight("struct User {}", Some("swift")).nonEmpty)
    assert(sut.highlight("def foo; end", Some("ruby")).nonEmpty)
    assert(sut.highlight("package main", Some("go")).nonEmpty)
    assert(sut.highlight("repeat 10", Some("hsp")).nonEmpty)
    assert(sut.highlight("goto *start", Some("nscripter")).nonEmpty)
    assert(sut.highlight("<expr> ::= \"a\"", Some("bnf")).nonEmpty)
    assert(sut.highlight("app.name=blog", Some("properties")).nonEmpty)
    assert(sut.highlight("<div></div>", Some("html")).nonEmpty)
    assert(sut.highlight("dependencies { }", Some("gradle")).nonEmpty)
    assert(sut.highlight("{{name}}", Some("mustache")).nonEmpty)
    assert(sut.highlight("program main", Some("fortran")).nonEmpty)
    assert(sut.highlight("a + b = c", Some("math")).nonEmpty)
  }

  private def resourceUrl(name: String): URL = {
    Option(getClass.getResource(resourcePath(name))).getOrElse {
      fail(s"Test resource not found: $name")
    }
  }

  private def resourcePath(name: String): String = {
    s"/services/tm4ehighlighterspec/$name"
  }
}
