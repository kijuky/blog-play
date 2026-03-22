package controllers

import play.api.i18n.Messages
import play.api.i18n.MessagesApi
import play.api.mvc.AbstractController
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import play.twirl.api.Html
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.SQL
import scalikejdbc.WrappedResultSet

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

final case class BlogItem(
  stableId: String,
  title: String,
  body: Html,
  isDraft: Boolean,
  displayDate: String
)

object BlogItem {
  def from(rs: WrappedResultSet)(using dateTime: BlogDateTime): BlogItem = {
    val publishedAt = rs.stringOpt("published_at").map(OffsetDateTime.parse)
    val modifiedAt = rs.stringOpt("modified_at").map(OffsetDateTime.parse)
    apply(
      rs.string("stable_id"),
      rs.string("title"),
      Html(rs.string("body")),
      publishedAt.isEmpty,
      dateTime.format(publishedAt.orElse(modifiedAt))
    )
  }
}

class BlogShowController(cc: ControllerComponents, messagesApi: MessagesApi)(
  using BlogDateTime
) extends AbstractController(cc) {
  private given ExecutionContext = cc.executionContext

  def show(stableId: String): Action[AnyContent] =
    Action.async { request =>
      given messages: Messages = messagesApi.preferred(request)
      DB.futureLocalTx { case given DBSession =>
        Future.successful(
          SQL(
            "select stable_id, title, body, published_at, modified_at from blogs where stable_id = ? limit 1"
          )
            .bind(stableId)
            .map(BlogItem.from)
            .single
            .apply()
        )
      }.map {
        case Some(post) => Ok(views.html.blog_show(post))
        case None       => NotFound(messages("blog.notFound"))
      }
    }
}
