package services

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.commonmark.renderer.html.HtmlWriter
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlNodeRendererFactory
import org.commonmark.renderer.NodeRenderer
import play.twirl.api.Html

import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*

final class MarkdownRenderer(baseDir: Path, highlighter: Option[CodeHighlighter] = None) {
  private val extensions = Seq(
    AutolinkExtension.create(),
    TablesExtension.create()
  )
  private val parser = Parser.builder().extensions(extensions.asJava).build()
  private val renderer = {
    val builder = HtmlRenderer.builder().extensions(extensions.asJava)
    highlighter.foreach { h =>
      builder.nodeRendererFactory(new CodeBlockRendererFactory(h))
    }
    builder.build()
  }

  def render(markdown: String, contentPath: String): Html = {
    val doc = parser.parse(markdown)
    val contentDir = baseDir.resolve(contentPath).normalize()

    doc.accept(new AbstractVisitor {
      override def visit(link: Link): Unit = {
        val destination = link.getDestination
        val child = link.getFirstChild
        child match {
          case text: Text if text.getLiteral == destination =>
            val decoded = decodeForDisplay(destination)
            if (decoded != destination) {
              text.setLiteral(decoded)
            }
          case _ => ()
        }
        super.visit(link)
      }

      override def visit(image: Image): Unit = {
        val destination = image.getDestination
        if (!isAbsoluteUrl(destination) && !destination.startsWith("data:")) {
          val imgPath = contentDir.resolve(destination).normalize()
          if (Files.exists(imgPath)) {
            val bytes = Files.readAllBytes(imgPath)
            val mime = Option(Files.probeContentType(imgPath)).getOrElse("application/octet-stream")
            val encoded = Base64.getEncoder.encodeToString(bytes)
            image.setDestination(s"data:$mime;base64,$encoded")
          }
        }
        super.visit(image)
      }
    })

    Html(renderer.render(doc))
  }

  private def decodeForDisplay(value: String): String = {
    // URLDecoder turns '+' into space, so convert spaces back to '+' for display.
    URLDecoder.decode(value, StandardCharsets.UTF_8).replace(" ", "+")
  }

  private def isAbsoluteUrl(value: String): Boolean = {
    value.startsWith("http://") || value.startsWith("https://")
  }
}

private final class CodeBlockRendererFactory(highlighter: CodeHighlighter) extends HtmlNodeRendererFactory {
  override def create(context: HtmlNodeRendererContext): NodeRenderer =
    new CodeBlockNodeRenderer(context, highlighter)
}

private final class CodeBlockNodeRenderer(
    context: HtmlNodeRendererContext,
    highlighter: CodeHighlighter
) extends NodeRenderer {
  override def getNodeTypes: java.util.Set[Class[? <: org.commonmark.node.Node]] =
    Set[Class[? <: org.commonmark.node.Node]](classOf[FencedCodeBlock]).asJava

  override def render(node: org.commonmark.node.Node): Unit = node match {
    case codeBlock: FencedCodeBlock =>
      val info = Option(codeBlock.getInfo).getOrElse("").trim
      val (language, fileName) = parseInfo(info)
      val literal = Option(codeBlock.getLiteral).getOrElse("")
      val html = highlighter.highlight(literal, language)

      val writer: HtmlWriter = context.getWriter
      writer.line()
      writer.raw("""<div class="code-block">""")
      fileName.foreach { name =>
        writer.raw("""<div class="code-header"><span class="code-filename">""")
        writer.raw(escapeHtml(name))
        writer.raw("</span></div>")
      }
      writer.raw("<pre><code")
      language.foreach { lang =>
        writer.raw(s""" class="language-$lang"""")
      }
      writer.raw(">")

      html match {
        case Some(value) => writer.raw(value)
        case None => writer.text(literal)
      }

      writer.raw("</code></pre></div>")
      writer.line()
    case _ => ()
  }

  private def parseInfo(info: String): (Option[String], Option[String]) = {
    val head = info.split("\\s+").headOption.getOrElse("")
    if (head.contains(":")) {
      val parts = head.split(":", 2)
      val lang = parts.headOption.map(_.trim).filter(_.nonEmpty)
      val name = parts.lift(1).map(_.trim).filter(_.nonEmpty)
      (lang, name)
    } else {
      val trimmed = head.trim
      if (trimmed.isEmpty) {
        (None, None)
      } else if (trimmed.contains(".") && !trimmed.startsWith(".")) {
        val ext = trimmed.split("\\.").lastOption.map(_.trim).filter(_.nonEmpty)
        val lang = ext.map(normalizeExtension)
        (lang, Some(trimmed))
      } else {
        (Some(trimmed), None)
      }
    }
  }

  private def normalizeExtension(ext: String): String = ext match {
    case "sbt" => "scala"
    case "sh" => "shell"
    case "bash" => "bash"
    case "zsh" => "zsh"
    case "cmd" => "cmd"
    case "bat" => "cmd"
    case "yml" => "yaml"
    case "js" => "javascript"
    case "gs" => "javascript"
    case "conf" => "hocon"
    case "dockerfile" => "dockerfile"
    case "c" => "c"
    case "h" => "c"
    case "cpp" => "cpp"
    case "cc" => "cpp"
    case "cxx" => "cpp"
    case "hpp" => "cpp"
    case "mm" => "objective-c"
    case "m" => "objective-c"
    case other => other
  }

  private def escapeHtml(value: String): String = {
    value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
  }
}
