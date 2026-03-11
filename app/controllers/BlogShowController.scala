package controllers

import play.api.mvc.AbstractController
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.twirl.api.Html
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.SQL

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

final case class BlogItem(
    stableId: String,
    title: String,
    body: Html,
    isDraft: Boolean,
    displayDate: String
)

object BlogItem {
  private val zoneId = ZoneId.of("Asia/Tokyo")
  private val fmt = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

  def from(
      stableId: String,
      title: String,
      body: Html,
      publishedAt: Option[OffsetDateTime],
      modifiedAt: Option[OffsetDateTime]
  ): BlogItem = {
    BlogItem(
      stableId,
      title,
      body,
      publishedAt.isEmpty,
      formatDate(publishedAt.orElse(modifiedAt))
    )
  }

  private def formatDate(value: Option[OffsetDateTime]): String = {
    value
      .map(dt => fmt.format(dt.atZoneSameInstant(zoneId)))
      .getOrElse("")
  }
}

class BlogShowController(cc: ControllerComponents) extends AbstractController(cc) {

  def show(stableId: String): Action[AnyContent] = Action {
    val postOpt = DB.autoCommit { case given DBSession =>
      SQL("select stable_id, title, body, published_at, modified_at from blogs where stable_id = ? limit 1")
        .bind(stableId)
        .map { rs =>
          val body = Html(rs.string("body"))
          BlogItem.from(
            rs.string("stable_id"),
            rs.string("title"),
            body,
            rs.stringOpt("published_at").map(OffsetDateTime.parse),
            rs.stringOpt("modified_at").map(OffsetDateTime.parse)
          )
        }
        .single
        .apply()
    }

    postOpt match {
      case Some(post) => Ok(views.html.blog_show(post))
      case None => NotFound("Blog not found")
    }
  }
}
