package services

import io.github.classgraph.ClassGraph
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import scalikejdbc.ConnectionPool
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.SQL

import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.ZoneId
import scala.jdk.CollectionConverters.*
import scala.util.Using

class BlogImporterSpec
    extends AnyFunSuite
    with BeforeAndAfterAll
    with BeforeAndAfterEach {
  private val sut = BlogImporter()(using ZoneId.systemDefault)
  private val noRenderer = NoRenderer()
  private val metas = {
    Using(ClassGraph().acceptPaths("services/blogimporterspec").scan())(
      _.getAllResources.asScala
        .filter(_.getPath.endsWith("meta.yaml"))
        .map(_.getURL)
        .toSeq
    ).getOrElse {
      fail("Test resource not found: services/blogimporterspec/**/meta.yaml")
    }
  }
  private val initSqlPath = resourcePath("services/blogimporterspec/init.sql")

  override def beforeAll(): Unit = {
    ConnectionPool.singleton(
      "jdbc:h2:mem:blogimporterspec;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "",
      ""
    )
  }

  override def beforeEach(): Unit = {
    DB.autoCommit { case given DBSession =>
      Files
        .readString(initSqlPath)
        .split(";")
        .map(_.trim)
        .filter(_.nonEmpty)
        .foreach(SQL(_).execute.apply())
    }
  }

  override def afterAll(): Unit = {
    ConnectionPool.closeAll()
  }

  test("importAllEither imports HTML blogs and tags") {
    DB.autoCommit { case given DBSession =>
      val metaUrl = findMeta("blog/01_test")
      val renderer = MarkdownRendererImpl()
      val actual = sut.importAllEither(Seq(metaUrl), renderer)
      assert(actual.isRight, actual.toString)

      val blogCount =
        SQL("select count(*) as c from blogs")
          .map(_.int("c"))
          .single
          .apply()
          .getOrElse(0)

      val tagCount =
        SQL("select count(*) as c from tags")
          .map(_.int("c"))
          .single
          .apply()
          .getOrElse(0)

      val linkCount =
        SQL("select count(*) as c from blog_tags")
          .map(_.int("c"))
          .single
          .apply()
          .getOrElse(0)

      assert(blogCount == 1)
      assert(tagCount == 2)
      assert(linkCount == 2)

      val body =
        SQL("select body from blogs limit 1")
          .map(_.string("body"))
          .single
          .apply()
          .getOrElse("")
      assert(body.contains("<h1>"))
      assert(!body.contains("# Hello"))
    }
  }

  test("importAllEither returns MissingRoot") {
    DB.autoCommit { case given DBSession =>
      val metaUri = findMeta("blog/01_test").toURI
      val missing = metaUri.resolve("no_such_dir_should_not_exist")
      assert(!File(missing).exists)

      val actual = sut.importAllEither(Seq(missing.toURL), noRenderer)
      assert(
        actual.left.exists(_.isInstanceOf[BlogImporter.ImportError.MissingRoot])
      )
    }
  }

  test("importAllEither returns MissingBody") {
    DB.autoCommit { case given DBSession =>
      val metaUrl = findMeta("invalid/missing_body/01_test")
      val actual = sut.importAllEither(Seq(metaUrl), noRenderer)
      assert(actual.left.exists {
        case BlogImporter.ImportError.MissingBody(path, _) =>
          path.getPath.endsWith("README.md")
        case _ =>
          false
      })
    }
  }

  test("importAllEither returns InvalidDate") {
    DB.autoCommit { case given DBSession =>
      val metaUrl = findMeta("invalid/invalid_date/01_test")
      val actual = sut.importAllEither(Seq(metaUrl), noRenderer)
      assert(actual.left.exists {
        case BlogImporter.ImportError.InvalidDate(_, field, value) =>
          field == "published_at" && value == "not-a-date"
        case _ =>
          false
      })
    }
  }

  test("importAllEither returns ParseError for non-array tags") {
    DB.autoCommit { case given DBSession =>
      val metaUrl = findMeta("invalid/tags_string/01_test")
      val actual = sut.importAllEither(Seq(metaUrl), noRenderer)
      assert(
        actual.left.exists(_.isInstanceOf[BlogImporter.ImportError.ParseError])
      )
    }
  }

  test("source resolution uses github vs archive source") {
    DB.autoCommit { case given DBSession =>
      val metaUrls = filterMetas("blog/")
      val actual = sut.importAllEither(metaUrls, noRenderer)
      assert(actual.isRight)

      val githubSource =
        SQL("select source from blogs where title = ? limit 1")
          .bind("Hello")
          .map(_.string("source"))
          .single
          .apply()
          .getOrElse("")
      assert(githubSource == "github")

      val archivedSource =
        SQL("select source from blogs where title = ? limit 1")
          .bind("Archived Hello")
          .map(_.string("source"))
          .single
          .apply()
          .getOrElse("")
      assert(archivedSource == "hatena_example")
    }
  }

  test("date normalization stores UTC Z") {
    DB.autoCommit { case given DBSession =>
      val metaUrl = findMeta("blog/02_offset_date")
      val result = sut.importAllEither(Seq(metaUrl), noRenderer)
      assert(result.isRight)

      val publishedAt =
        SQL("select published_at from blogs where title = ? limit 1")
          .bind("Offset Date")
          .map(_.stringOpt("published_at"))
          .single
          .apply()
          .flatten
          .getOrElse("")
      assert(publishedAt == "2026-03-10T00:00:00Z")
    }
  }

  test("importAllEither accepts numeric-only directory names") {
    DB.autoCommit { case given DBSession =>
      val metaUrl = findMeta("blog/03")
      val result = sut.importAllEither(Seq(metaUrl), noRenderer)
      assert(result.isRight)

      val stableId =
        SQL("select stable_id from blogs where source = ? limit 1")
          .bind("github")
          .map(_.string("stable_id"))
          .single
          .apply()
          .getOrElse("")
      assert(stableId == "github-03")
    }
  }

  private def resourcePath(name: String): Path = {
    val url =
      Option(getClass.getClassLoader.getResource(name))
        .getOrElse { fail(s"Test resource not found: $name") }
    Paths.get(url.toURI)
  }

  private def findMeta(name: String): URL = {
    filterMetas(name).headOption
      .getOrElse { fail(s"Test resource not found: $name") }
  }

  private def filterMetas(name: String): Seq[URL] = {
    metas.filter(_.getPath.contains(name))
  }
}
