package controllers

import play.api.mvc.AbstractController
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import scalikejdbc.DB
import scalikejdbc.SQL

final case class BlogListItem(
    id: Long,
    title: String,
    publishedAt: Option[String],
    modifiedAt: Option[String]
)

final case class BlogListViewItem(
    id: Long,
    title: String,
    displayDate: String
)

object BlogListViewItem {
  def from(item: BlogListItem): BlogListViewItem = {
    BlogListViewItem(
      item.id,
      item.title,
      item.publishedAt.orElse(item.modifiedAt).getOrElse("")
    )
  }
}

class BlogListController(cc: ControllerComponents) extends AbstractController(cc) {
  def list(): Action[AnyContent] = Action {
    val items = DB.autoCommit { case given scalikejdbc.DBSession =>
      SQL("select id, title, published_at, modified_at from posts order by coalesce(modified_at, published_at) desc")
        .map { rs =>
          BlogListItem(
            rs.long("id"),
            rs.string("title"),
            rs.stringOpt("published_at"),
            rs.stringOpt("modified_at")
          )
        }
        .list
        .apply()
    }

    val viewItems = items.map(BlogListViewItem.from)

    Ok(views.html.blog_list(viewItems))
  }
}
