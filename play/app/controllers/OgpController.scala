package controllers

import play.api.libs.json.Json
import play.api.mvc.AbstractController
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import services.OgpClient

import java.net.URI
import scala.util.Try

class OgpController(cc: ControllerComponents, ogpClient: OgpClient)
    extends AbstractController(cc) {

  def fetch(url: String, text: Option[String]): Action[AnyContent] = Action {
    validateUrl(url) match {
      case Some(validUrl) =>
        val anchorText =
          text.map(_.trim).filter(_.nonEmpty).filter(_ != validUrl)
        ogpClient.fetch(validUrl, anchorText) match {
          case Some(metadata) =>
            Ok(
              Json.obj(
                "url" -> validUrl,
                "title" -> metadata.title,
                "description" -> metadata.description,
                "imageUrl" -> metadata.imageUrl,
                "siteName" -> metadata.siteName,
                "fallback" -> metadata.fallback
              )
            )
          case None =>
            Ok(
              Json.obj(
                "url" -> validUrl,
                "title" -> anchorText.getOrElse(hostOf(validUrl)),
                "description" -> Some(validUrl),
                "imageUrl" -> Some(s"${originOf(validUrl)}/favicon.ico"),
                "siteName" -> Some(hostOf(validUrl)),
                "fallback" -> true
              )
            )
        }
      case None =>
        BadRequest
    }
  }

  private def validateUrl(raw: String): Option[String] =
    Try(URI.create(raw)).toOption
      .filter(_.getHost != null)
      .filter(uri => uri.getScheme == "http" || uri.getScheme == "https")
      .map(_.toString)

  private def hostOf(url: String): String =
    Try(URI.create(url).getHost).toOption.filter(_ != null).getOrElse(url)

  private def originOf(url: String): String = {
    val uri = URI.create(url)
    s"${uri.getScheme}://${uri.getHost}"
  }
}
