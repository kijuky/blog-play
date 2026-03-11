package controllers

import play.api.mvc.AbstractController
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.SQL
import scalikejdbc.WrappedResultSet

import java.time.OffsetDateTime

final case class BlogListItem(
    stableId: String,
    title: String,
    publishedAt: Option[OffsetDateTime],
    modifiedAt: Option[OffsetDateTime]
)

object BlogListItem {
  def from(rs: WrappedResultSet): BlogListItem = {
    BlogListItem(
      rs.string("stable_id"),
      rs.string("title"),
      rs.stringOpt("published_at").map(OffsetDateTime.parse),
      rs.stringOpt("modified_at").map(OffsetDateTime.parse)
    )
  }
}

final case class BlogListViewItem(
    stableId: String,
    title: String,
    isDraft: Boolean,
    displayDate: String
)

object BlogListViewItem {
  def from(item: BlogListItem)(using dateTime: BlogDateTime): BlogListViewItem = {
    BlogListViewItem(
      item.stableId,
      item.title,
      item.publishedAt.isEmpty,
      dateTime.format(item.publishedAt.orElse(item.modifiedAt))
    )
  }
}

class BlogListController(cc: ControllerComponents)(using BlogDateTime) extends AbstractController(cc) {
  def list(): Action[AnyContent] = Action {
    val items = DB.autoCommit { case given DBSession =>
      SQL(
        "select stable_id, title, published_at, modified_at from blogs order by coalesce(modified_at, published_at) desc"
      )
        .map(BlogListItem.from)
        .list
        .apply()
    }

    val viewItems = items.map(BlogListViewItem.from(_))

    Ok(views.html.blog_list(viewItems))
  }
}
