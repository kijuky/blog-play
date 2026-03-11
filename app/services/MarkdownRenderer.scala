package services

import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Image
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import play.twirl.api.Html

import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import scala.jdk.CollectionConverters._

final class MarkdownRenderer(baseDir: Path) {
  private val extensions = List(AutolinkExtension.create()).asJava
  private val parser = Parser.builder().extensions(extensions).build()
  private val renderer = HtmlRenderer.builder().extensions(extensions).build()

  def render(markdown: String, contentPath: String): Html = {
    val doc = parser.parse(markdown)
    val contentDir = baseDir.resolve(contentPath).normalize()

    doc.accept(new AbstractVisitor {
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

  private def isAbsoluteUrl(value: String): Boolean = {
    value.startsWith("http://") || value.startsWith("https://")
  }
}
