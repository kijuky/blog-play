package controllers

import play.api.mvc.AbstractController
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.twirl.api.Html
import scalikejdbc.DB
import scalikejdbc.SQL

final case class BlogItem(
    id: Long,
    title: String,
    body: Html,
    displayDate: String
)

object BlogItem {
  def from(
      id: Long,
      title: String,
      body: Html,
      publishedAt: Option[String],
      modifiedAt: Option[String]
  ): BlogItem = {
    BlogItem(
      id,
      title,
      body,
      publishedAt.orElse(modifiedAt).getOrElse("")
    )
  }
}

class BlogShowController(cc: ControllerComponents) extends AbstractController(cc) {

  def show(id: Long): Action[AnyContent] = Action {
    val postOpt = DB.autoCommit { case given scalikejdbc.DBSession =>
      SQL("select id, title, body, published_at, modified_at from blogs where id = ? limit 1")
        .bind(id)
        .map { rs =>
          val body = Html(rs.string("body"))
          BlogItem.from(
            rs.long("id"),
            rs.string("title"),
            body,
            rs.stringOpt("published_at"),
            rs.stringOpt("modified_at")
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
