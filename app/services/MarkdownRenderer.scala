package services

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import play.twirl.api.Html

import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*

final class MarkdownRenderer(baseDir: Path) {
  private val extensions = Seq(
    AutolinkExtension.create(),
    TablesExtension.create()
  )
  private val parser = Parser.builder().extensions(extensions.asJava).build()
  private val renderer = HtmlRenderer.builder().extensions(extensions.asJava).build()

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
