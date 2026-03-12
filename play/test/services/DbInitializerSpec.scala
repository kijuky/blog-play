package services

import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import scalikejdbc.ConnectionPool
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.SQL

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class DbInitializerSpec extends AnyFunSuite with BeforeAndAfterAll {
  override def beforeAll(): Unit = {
    ConnectionPool.singleton(
      "jdbc:h2:mem:dbinitializerspec;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "",
      ""
    )
  }

  override def afterAll(): Unit = {
    ConnectionPool.closeAll()
  }

  test("initFromSql creates tables") {
    val sqlText =
      Files.readString(Path.of("conf/init.sql"), StandardCharsets.UTF_8)
    DbInitializer.initFromSql(sqlText)

    DB.autoCommit { case given DBSession =>
      val tables =
        SQL(
          "select lower(table_name) as name from information_schema.tables where table_schema = 'PUBLIC'"
        )
          .map(_.string("name"))
          .list
          .apply()

      assert(tables.contains("blogs"))
      assert(tables.contains("tags"))
      assert(tables.contains("blog_tags"))
    }
  }

  test("initFromSql can run twice") {
    val sqlText =
      Files.readString(Path.of("conf/init.sql"), StandardCharsets.UTF_8)
    DbInitializer.initFromSql(sqlText)
    DbInitializer.initFromSql(sqlText)

    DB.autoCommit { case given DBSession =>
      val tables =
        SQL(
          "select lower(table_name) as name from information_schema.tables where table_schema = 'PUBLIC'"
        )
          .map(_.string("name"))
          .list
          .apply()
      assert(tables.contains("blogs"))
      assert(tables.contains("tags"))
      assert(tables.contains("blog_tags"))
    }
  }
}
