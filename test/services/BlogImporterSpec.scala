package services

import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import scalikejdbc.ConnectionPool
import scalikejdbc.DB
import scalikejdbc.SQL

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class BlogImporterSpec extends AnyFunSuite with BeforeAndAfterAll {
  private val blogRoot: Path = resourcePath("services/blogimporterspec/blog")
  private val initSqlPath: Path = resourcePath("services/blogimporterspec/init.sql")

  override def beforeAll(): Unit = {
    ConnectionPool.singleton("jdbc:sqlite::memory:", "", "")

    DB.autoCommit { case given scalikejdbc.DBSession =>
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

  test("importAllEither imports blogs and tags") {
    val result = BlogImporter.importAllEither(blogRoot)
    assert(result.isRight)

    DB.autoCommit { case given scalikejdbc.DBSession =>
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
    }
  }

  private def resourcePath(name: String): Path = {
    val url = Option(getClass.getClassLoader.getResource(name)).getOrElse {
      fail(s"Test resource not found: $name")
    }
    Paths.get(url.toURI)
  }
}
