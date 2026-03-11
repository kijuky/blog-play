package controllers

import play.api.mvc.AbstractController
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.SQL

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

final case class BlogListItem(
    stableId: String,
    title: String,
    publishedAt: Option[OffsetDateTime],
    modifiedAt: Option[OffsetDateTime]
)

final case class BlogListViewItem(
    stableId: String,
    title: String,
    isDraft: Boolean,
    displayDate: String
)

object BlogListViewItem {
  private val zoneId = ZoneId.of("Asia/Tokyo")
  private val fmt = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

  def from(item: BlogListItem): BlogListViewItem = {
    BlogListViewItem(
      item.stableId,
      item.title,
      item.publishedAt.isEmpty,
      formatDate(item.publishedAt.orElse(item.modifiedAt))
    )
  }

  private def formatDate(value: Option[OffsetDateTime]): String = {
    value
      .map(dt => fmt.format(dt.atZoneSameInstant(zoneId)))
      .getOrElse("")
  }
}

class BlogListController(cc: ControllerComponents) extends AbstractController(cc) {
  def list(): Action[AnyContent] = Action {
    val items = DB.autoCommit { case given DBSession =>
      SQL(
        "select stable_id, title, published_at, modified_at from blogs order by coalesce(modified_at, published_at) desc"
      )
        .map { rs =>
          BlogListItem(
            rs.string("stable_id"),
            rs.string("title"),
            rs.stringOpt("published_at").map(OffsetDateTime.parse),
            rs.stringOpt("modified_at").map(OffsetDateTime.parse)
          )
        }
        .list
        .apply()
    }

    val viewItems = items.map(BlogListViewItem.from)

    Ok(views.html.blog_list(viewItems))
  }
}
