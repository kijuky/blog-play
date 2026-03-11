package services

import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import scalikejdbc.ConnectionPool
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.SQL

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class BlogImporterSpec extends AnyFunSuite with BeforeAndAfterAll with BeforeAndAfterEach {
  private val blogRoot: Path = resourcePath("services/blogimporterspec/blog")
  private val initSqlPath: Path = resourcePath("services/blogimporterspec/init.sql")

  override def beforeAll(): Unit = {
    ConnectionPool.singleton("jdbc:h2:mem:blogimporterspec;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "", "")
  }

  override def beforeEach(): Unit = {
    DB.autoCommit { case given DBSession =>
      Files.readString(initSqlPath)
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
    val result = BlogImporter.importAllEither(blogRoot.resolve("01_test"))
    assert(result.isRight)

    DB.autoCommit { case given DBSession =>
      val blogCount = SQL("select count(*) as c from blogs")
        .map(_.int("c"))
        .single
        .apply()
        .getOrElse(0)

      val tagCount = SQL("select count(*) as c from tags")
        .map(_.int("c"))
        .single
        .apply()
        .getOrElse(0)

      val linkCount = SQL("select count(*) as c from blog_tags")
        .map(_.int("c"))
        .single
        .apply()
        .getOrElse(0)

      assert(blogCount == 1)
      assert(tagCount == 2)
      assert(linkCount == 2)

      val body = SQL("select body from blogs limit 1")
        .map(_.string("body"))
        .single
        .apply()
        .getOrElse("")
      assert(body.contains("<h1>"))
      assert(!body.contains("# Hello"))
    }
  }

  test("importAllEither returns MissingRoot") {
    val base = resourcePath("services/blogimporterspec")
    val missing = base.resolve("no_such_dir_should_not_exist")
    assert(!Files.exists(missing))

    val result = BlogImporter.importAllEither(missing)
    assert(result.left.exists(_.isInstanceOf[BlogImporter.ImportError.MissingRoot]))
  }

  test("importAllEither returns MissingBody") {
    val root = resourcePath("services/blogimporterspec/invalid/missing_body")
    val result = BlogImporter.importAllEither(root)
    assert(result.left.exists {
      case BlogImporter.ImportError.MissingBody(path) => path.getFileName.toString == "README.md"
      case _ => false
    })
  }

  test("importAllEither returns InvalidDate") {
    val root = resourcePath("services/blogimporterspec/invalid/invalid_date")
    val result = BlogImporter.importAllEither(root)
    assert(result.left.exists {
      case BlogImporter.ImportError.InvalidDate(_, field, value) =>
        field == "published_at" && value == "not-a-date"
      case _ => false
    })
  }

  test("importAllEither returns ParseError for non-array tags") {
    val root = resourcePath("services/blogimporterspec/invalid/tags_string")
    val result = BlogImporter.importAllEither(root)
    assert(result.left.exists(_.isInstanceOf[BlogImporter.ImportError.ParseError]))
  }

  test("source resolution uses github vs archive source") {
    val result = BlogImporter.importAllEither(blogRoot)
    assert(result.isRight)

    DB.autoCommit { case given DBSession =>
      val githubSource = SQL("select source from blogs where title = ? limit 1")
        .bind("Hello")
        .map(_.string("source"))
        .single
        .apply()
        .getOrElse("")
      assert(githubSource == "github")

      val archivedSource = SQL("select source from blogs where title = ? limit 1")
        .bind("Archived Hello")
        .map(_.string("source"))
        .single
        .apply()
        .getOrElse("")
      assert(archivedSource == "hatena_example")
    }
  }

  test("date normalization stores UTC Z") {
    val result = BlogImporter.importAllEither(blogRoot.resolve("02_offset_date"))
    assert(result.isRight)

    DB.autoCommit { case given DBSession =>
      val publishedAt = SQL("select published_at from blogs where title = ? limit 1")
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
    val result = BlogImporter.importAllEither(blogRoot.resolve("03"))
    assert(result.isRight)

    DB.autoCommit { case given DBSession =>
      val stableId = SQL("select stable_id from blogs where source = ? limit 1")
        .bind("github")
        .map(_.string("stable_id"))
        .single
        .apply()
        .getOrElse("")
      assert(stableId == "github-03")
    }
  }

  private def resourcePath(name: String): Path = {
    val url = Option(getClass.getClassLoader.getResource(name)).getOrElse {
      fail(s"Test resource not found: $name")
    }
    Paths.get(url.toURI)
  }
}
