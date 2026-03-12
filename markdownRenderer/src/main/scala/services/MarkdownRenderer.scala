package services

import org.apache.commons.io.FilenameUtils
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext
import org.commonmark.renderer.html.HtmlNodeRendererFactory
import org.commonmark.renderer.html.HtmlRenderer

import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.NoSuchFileException
import java.util.Base64
import scala.annotation.nowarn
import scala.io.Codec
import scala.io.Source
import scala.jdk.CollectionConverters.*
import scala.util.Using

object MarkdownRenderer {
  enum RendererError derives CanEqual:
    case MissingContents(url: URL)
    case MissingImage(url: URL)
    case IoError(url: URL, cause: Throwable)
}

trait MarkdownRenderer {

  /** レンダリング結果を返します。
    *
    * @param contentUrl
    *   レンダリング対象のコンテンツを指すURL
    * @return
    *   レンダリング結果
    */
  def render(
    contentUrl: URL
  )(using codec: Codec): Either[MarkdownRenderer.RendererError, String] =
    Using(Source.fromURL(contentUrl)) { contents =>
      render(contents.getLines.mkString("\n"), Some(contentUrl))
    }.toEither.left.map(MarkdownRenderer.RendererError.IoError(contentUrl, _))

  def render(markdown: String): String =
    render(markdown, None)

  /** レンダリング結果を返します。
    *
    * @param markdown
    *   レンダリング対象
    * @param contentUrl
    *   レンダリング対象からの相対パスを解決するためのURL。レンダリング対象が相対パスリンクを持っていないならNoneでよい。
    * @return
    *   レンダリング結果
    */
  def render(markdown: String, contentUrl: Option[URL]): String
}

final class NoRenderer extends MarkdownRenderer {
  override def render(markdown: String, contentUrl: Option[URL]): String =
    markdown
}

final class MarkdownRendererImpl(highlighter: Option[CodeHighlighter] = None)
    extends MarkdownRenderer {
  private val extensions =
    Seq(AutolinkExtension.create(), TablesExtension.create())
  private val parser = Parser.builder.extensions(extensions.asJava).build()
  private val renderer = {
    val builder = HtmlRenderer.builder.extensions(extensions.asJava)
    highlighter.foreach { h =>
      builder.nodeRendererFactory(CodeBlockRendererFactory(h))
    }
    builder.build()
  }

  override def render(markdown: String, contentUrl: Option[URL]): String = {
    val doc = parser.parse(markdown)

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
          case _ =>
            ()
        }
        super.visit(link)
      }

      override def visit(image: Image): Unit = {
        val destination = image.getDestination
        if (!isAbsoluteUrl(destination) && !destination.startsWith("data:")) {
          contentUrl
            .map { url =>
              // contentUrlが不透明(jarスキーム)の場合、URI#resolveが使えない。
              // そのため、パスを手動で解析構築する
              @nowarn("msg=deprecated")
              val resolved = URL(url, destination)
              resolved
            }
            .foreach { imgUrl =>
              val bytes: Array[Byte] =
                Using(imgUrl.openStream)(_.readAllBytes).getOrElse(
                  throw NoSuchFileException(s"no such file: $imgUrl")
                )
              val mime =
                Option(imgUrl.openConnection.getContentType)
                  .getOrElse("application/octet-stream")
              val encoded = Base64.getEncoder.encodeToString(bytes)
              image.setDestination(s"data:$mime;base64,$encoded")
            }
        }
        super.visit(image)
      }
    })

    renderer.render(doc)
  }

  private def decodeForDisplay(value: String): String = {
    // URLDecoder turns '+' into space, so convert spaces back to '+' for display.
    URLDecoder.decode(value, StandardCharsets.UTF_8).replace(" ", "+")
  }

  private def isAbsoluteUrl(value: String): Boolean = {
    value.startsWith("http://") || value.startsWith("https://")
  }
}

private final class CodeBlockRendererFactory(highlighter: CodeHighlighter)
    extends HtmlNodeRendererFactory {
  override def create(context: HtmlNodeRendererContext): NodeRenderer =
    CodeBlockNodeRenderer(context, highlighter)
}

private final class CodeBlockNodeRenderer(
  context: HtmlNodeRendererContext,
  highlighter: CodeHighlighter
) extends NodeRenderer {
  override def getNodeTypes: java.util.Set[Class[? <: Node]] =
    java.util.Set.of(classOf[FencedCodeBlock])

  override def render(node: Node): Unit =
    node match {
      case codeBlock: FencedCodeBlock =>
        val info = Option(codeBlock.getInfo).getOrElse("").trim
        val (language, fileName) = parseInfo(info, highlighter)
        val literal = Option(codeBlock.getLiteral).getOrElse("")
        val html = highlighter.highlight(literal, language)

        val writer = context.getWriter
        writer.line()
        writer.raw("""<div class="code-block">""")
        fileName.foreach { name =>
          writer.raw(
            """<div class="code-header"><span class="code-filename">"""
          )
          writer.raw(escapeHtml(name))
          writer.raw("</span></div>")
        }

        if (language.contains("mermaid")) {
          writer.raw("""<pre class="mermaid">""")
          writer.raw(escapeHtml(literal))
          writer.raw("</pre></div>")
        } else {
          writer.raw("<pre><code")
          language.foreach { lang =>
            writer.raw(s""" class="language-$lang"""")
          }
          writer.raw(">")

          html match {
            case Some(value) => writer.raw(value)
            case None        => writer.text(literal)
          }

          writer.raw("</code></pre></div>")
        }
        writer.line()
      case _ =>
        ()
    }

  private def parseInfo(
    info: String,
    highlighter: CodeHighlighter
  ): (Option[String], Option[String]) = {
    val head = info.split("\\s+").headOption.getOrElse("")
    if (head.contains(":")) {
      val parts = head.split(":", 2)
      val lang = parts.headOption.map(_.trim).filter(_.nonEmpty)
      val name = parts.lift(1).map(_.trim).filter(_.nonEmpty)
      (lang.map(normalizeLanguageTag), name)
    } else {
      val trimmed = head.trim
      if (trimmed.isEmpty) {
        (None, None)
      } else if (trimmed.startsWith(":")) {
        val name = trimmed.drop(1).trim
        if (name.isEmpty) {
          (None, None)
        } else {
          val lang =
            highlighter.languageForExtension(FilenameUtils.getExtension(name))
          (lang, Some(name))
        }
      } else if (trimmed.contains(".") && !trimmed.endsWith(".")) {
        val lang =
          highlighter.languageForExtension(FilenameUtils.getExtension(trimmed))
        (lang, Some(trimmed))
      } else {
        val lang = Some(normalizeLanguageTag(trimmed))
        (lang, None)
      }
    }
  }

  private def normalizeLanguageTag(tag: String): String = {
    tag.trim.toLowerCase match {
      case "amm"           => "scala"
      case "config"        => "hocon"
      case "conf"          => "hocon"
      case "yaml"          => "yaml"
      case "yml"           => "yaml"
      case "shell-session" => "console"
      case "terminal"      => "console"
      case "sh"            => "shell"
      case "js"            => "javascript"
      case "dockerfile"    => "dockerfile"
      case "c++"           => "cpp"
      case "objective_c"   => "objective-c"
      case "bat"           => "cmd"
      case other           => other
    }
  }

  private def escapeHtml(value: String): String = {
    value
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
  }
}
