package services

import org.virtuslab.yaml.*
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.SQL

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.{Instant, LocalDateTime, OffsetDateTime, ZoneOffset}
import scala.util.{Try, Using}
import scala.jdk.CollectionConverters.*

object BlogImporter {
  enum ImportError derives CanEqual:
    case InvalidDate(path: Path, field: String, value: String)
    case MissingBody(path: Path)
    case MissingRoot(path: Path)
    case IoError(path: Path, cause: Throwable)
    case ParseError(path: Path, message: String)

  private final case class Meta(
      title: Option[String],
      published_at: Option[String],
      modified_at: Option[String],
      tags: Option[Seq[String]]
  ) derives YamlDecoder

  def importAllEither(root: Path): Either[ImportError, Unit] = {
    filesUnder(root)
      .map(_.filter(_.getFileName.toString == "meta.yaml"))
      .flatMap(runImportTx(root, _))
  }

  private def runImportTx(root: Path, metaFiles: Seq[Path]): Either[ImportError, Unit] = {
    DB.localTx { case given DBSession =>
      val markdown = new MarkdownRenderer(root)
      metaFiles.foldLeft[Either[ImportError, Unit]](Right(())) { (acc, metaPath) =>
        acc.flatMap { _ =>
          readMeta(metaPath).flatMap { meta =>
            for {
              bodyMarkdown <- readBody(metaPath)
              publishedAt <- normalizeDate(metaPath, "published_at", meta.published_at)
              modifiedAt <- normalizeDate(metaPath, "modified_at", meta.modified_at)
            } yield {
              val source = resolveSource(root, metaPath)
              val contentPath = root.relativize(metaPath.getParent).toString.replace('\\', '/')
              val bodyHtml = markdown.render(bodyMarkdown, contentPath).body
              val blogId = upsertBlog(meta, bodyHtml, source, publishedAt, modifiedAt)
              meta.tags.getOrElse(Nil).foreach { tag =>
                val tagId = findTagId(tag).getOrElse(insertTag(tag))
                insertBlogTag(blogId, tagId)
              }
            }
          }
        }
      }
    }
  }

  private def upsertBlog(
      meta: Meta,
      bodyHtml: String,
      source: String,
      publishedAt: Option[String],
      modifiedAt: Option[String]
  )(using session: DBSession): Long = {
    val title = meta.title.getOrElse("")
    SQL(
      """
        |insert into blogs (title, body, published_at, modified_at, source)
        |values (?, ?, ?, ?, ?)
        |""".stripMargin
    ).bind(title, bodyHtml, publishedAt.orNull, modifiedAt.orNull, source)
      .updateAndReturnGeneratedKey
      .apply()
  }

  private def insertTag(name: String)(using session: DBSession): Long = {
    SQL("insert into tags (name) values (?)")
      .bind(name)
      .updateAndReturnGeneratedKey
      .apply()
  }

  private def insertBlogTag(blogId: Long, tagId: Long)(using session: DBSession): Unit = {
    SQL(
      """
        |insert into blog_tags (blog_id, tag_id)
        |select ?, ?
        |where not exists (
        |  select 1 from blog_tags where blog_id = ? and tag_id = ?
        |)
        |""".stripMargin
    ).bind(blogId, tagId, blogId, tagId)
      .update
      .apply()
  }

  private def readMeta(path: Path): Either[ImportError, Meta] = {
    val text = Files.readString(path, StandardCharsets.UTF_8)
    text.as[Meta].left.map(error => ImportError.ParseError(path, error.toString))
  }

  private def readBody(metaPath: Path): Either[ImportError, String] = {
    val readmePath = metaPath.getParent.resolve("README.md")
    if (!Files.exists(readmePath)) {
      Left(ImportError.MissingBody(readmePath))
    } else {
      Try(Files.readString(readmePath, StandardCharsets.UTF_8))
        .toEither.left.map(ImportError.IoError(readmePath, _))
    }
  }

  private def normalizeDate(
      metaPath: Path,
      field: String,
      value: Option[String]
  ): Either[ImportError, Option[String]] = {
    value match {
      case None => Right(None)
      case Some(raw) =>
        parseInstant(raw)
          .map(instant => Some(instant.toString))
          .toRight(ImportError.InvalidDate(metaPath, field, raw))
    }
  }

  private def parseInstant(raw: String): Option[Instant] = {
    Try(OffsetDateTime.parse(raw).toInstant)
      .orElse(Try(Instant.parse(raw)))
      .orElse(Try(LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC)))
      .toOption
  }

  private def findTagId(name: String)(using session: DBSession): Option[Long] = {
    SQL("select id from tags where name = ? limit 1")
      .bind(name)
      .map(_.long("id"))
      .single
      .apply()
  }

  private def resolveSource(root: Path, metaPath: Path): String = {
    val relative = root.relativize(metaPath).toString.replace('\\', '/')
    val parts = relative.split("/").toSeq.filter(_.nonEmpty)

    parts match {
      case "00_archive" +: source +: _ => source
      case _ => "github"
    }
  }

  private def filesUnder(root: Path): Either[ImportError, Seq[Path]] = {
    if (!Files.exists(root)) {
      Left(ImportError.MissingRoot(root))
    } else {
      Using(Files.walk(root))(_.toList.asScala.toSeq)
        .toEither.left.map(ImportError.IoError(root, _))
    }
  }
}
