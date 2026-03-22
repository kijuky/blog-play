package services

import org.apache.commons.io.FilenameUtils
import org.virtuslab.yaml.*
import scalikejdbc.DBSession
import scalikejdbc.SQL

import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import scala.annotation.nowarn
import scala.io.Codec
import scala.io.Source
import scala.util.Try
import scala.util.Using

object BlogImporter {
  enum ImportError derives CanEqual:
    case InvalidDate(url: URL, field: String, value: String)
    case MissingBody(url: URL, cause: MarkdownRenderer.RendererError)
    case MissingRoot(url: URL, cause: Throwable)
    case IoError(url: URL, cause: Throwable)
    case ParseError(url: URL, cause: YamlError)
    case DirectoryError(url: URL, message: String)

  private final case class Meta(
    title: Option[String],
    published_at: Option[String],
    modified_at: Option[String],
    tags: Option[Seq[String]]
  ) derives YamlDecoder
}

final class BlogImporter()(using zoneId: ZoneId) {
  import BlogImporter.*

  def importAllEither(metaUrls: Seq[URL], renderer: MarkdownRenderer)(using
    DBSession
  ): Either[ImportError, Unit] = {
    LazyList
      .from(metaUrls)
      .map(runImport(_, renderer))
      .collectFirst { case l @ Left(_) => l }
      .getOrElse(Right(()))
  }

  def runImport(metaUrl: URL, renderer: MarkdownRenderer)(using
    Codec,
    DBSession
  ): Either[ImportError, Unit] = {
    readMeta(metaUrl).flatMap { meta =>
      @nowarn("msg=deprecated")
      val readmeUrl = URL(metaUrl, "README.md")
      for {
        source = resolveSource(metaUrl)
        stableId <- buildStableId(metaUrl, source)
        publishedAt <-
          normalizeDate(metaUrl, "published_at", meta.published_at)
        modifiedAt <-
          normalizeDate(metaUrl, "modified_at", meta.modified_at)
        bodyHtml <-
          renderer
            .render(readmeUrl)
            .left
            .map(ImportError.MissingBody(readmeUrl, _))
      } yield {
        val blogId =
          upsertBlog(meta, stableId, bodyHtml, source, publishedAt, modifiedAt)
        meta.tags.getOrElse(Nil).foreach { tag =>
          val tagId = findTagId(tag).getOrElse(insertTag(tag))
          insertBlogTag(blogId, tagId)
        }
      }
    }
  }

  private def upsertBlog(
    meta: Meta,
    stableId: String,
    bodyHtml: String,
    source: String,
    publishedAt: Option[String],
    modifiedAt: Option[String]
  )(using DBSession): Long = {
    val title = meta.title.getOrElse("")
    SQL("""
        |insert into blogs (stable_id, title, body, published_at, modified_at, source)
        |values (?, ?, ?, ?, ?, ?)
        |""".stripMargin)
      .bind(
        stableId,
        title,
        bodyHtml,
        publishedAt.orNull,
        modifiedAt.orNull,
        source
      )
      .updateAndReturnGeneratedKey
      .apply()
  }

  private def insertTag(name: String)(using DBSession): Long = {
    SQL("insert into tags (name) values (?)")
      .bind(name)
      .updateAndReturnGeneratedKey
      .apply()
  }

  private def insertBlogTag(
    blogId: Long,
    tagId: Long
  )(using DBSession): Unit = {
    SQL("""
        |insert into blog_tags (blog_id, tag_id)
        |select ?, ?
        |where not exists (
        |  select 1 from blog_tags where blog_id = ? and tag_id = ?
        |)
        |""".stripMargin)
      .bind(blogId, tagId, blogId, tagId)
      .update
      .apply()
  }

  private def readMeta(metaUrl: URL): Either[ImportError, Meta] = {
    Using(Source.fromURL(metaUrl))(
      _.getLines
        .mkString("\n")
        .as[Meta]
        .left
        .map(ImportError.ParseError(metaUrl, _))
    ).toEither.left
      .map(ImportError.MissingRoot(metaUrl, _))
      .flatMap(identity)
  }

  private def normalizeDate(
    metaUrl: URL,
    field: String,
    value: Option[String]
  ): Either[ImportError, Option[String]] = {
    value match {
      case None =>
        Right(None)
      case Some(raw) =>
        parseInstant(raw)
          .map(instant => Some(instant.toString))
          .toRight(ImportError.InvalidDate(metaUrl, field, raw))
    }
  }

  private def parseInstant(raw: String): Option[Instant] = {
    Try(OffsetDateTime.parse(raw).toInstant)
      .orElse(Try(Instant.parse(raw)))
      .orElse(Try(LocalDateTime.parse(raw).atZone(zoneId).toInstant))
      .toOption
  }

  private def findTagId(name: String)(using DBSession): Option[Long] = {
    SQL("select id from tags where name = ? limit 1")
      .bind(name)
      .map(_.long("id"))
      .single
      .apply()
  }

  private def resolveSource(metaUrl: URL): String = {
    metaUrl.getPath.split("/").reverse match {
      case Array("meta.yaml", _, source, "00_archive", _*) => source
      case _                                               => "github"
    }
  }

  private def buildStableId(
    metaUrl: URL,
    source: String
  ): Either[ImportError, String] = {
    val path = FilenameUtils.getPathNoEndSeparator(metaUrl.getPath)
    val dirName = FilenameUtils.getName(path)
    val digitPrefix = dirName.takeWhile(_.isDigit)
    val underscorePrefix =
      dirName.indexOf('_') match {
        case i if i > 0 => dirName.substring(0, i)
        case _          => ""
      }

    val prefix =
      if (digitPrefix.nonEmpty) digitPrefix
      else if (underscorePrefix.nonEmpty) underscorePrefix
      else ""

    if (prefix.isEmpty)
      Left(
        ImportError
          .DirectoryError(metaUrl, s"Invalid blog directory name: $dirName")
      )
    else
      Right(s"$source-$prefix")
  }
}
