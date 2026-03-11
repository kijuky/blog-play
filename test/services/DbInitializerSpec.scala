package services

import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import scalikejdbc.ConnectionPool
import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.SQL

import java.nio.charset.StandardCharsets
import java.nio.file.Path

class DbInitializerSpec extends AnyFunSuite with BeforeAndAfterAll {
  override def beforeAll(): Unit = {
    ConnectionPool.singleton("jdbc:sqlite::memory:", "", "")
  }

  override def afterAll(): Unit = {
    ConnectionPool.closeAll()
  }

  test("initFromSql creates tables") {
    val sqlText = java.nio.file.Files.readString(Path.of("conf/init.sql"), StandardCharsets.UTF_8)
    DbInitializer.initFromSql(sqlText)

    DB.autoCommit { case given DBSession =>
      val tables = SQL("select name from sqlite_master where type='table'")
        .map(_.string("name"))
        .list
        .apply()

      assert(tables.contains("blogs"))
      assert(tables.contains("tags"))
      assert(tables.contains("blog_tags"))
    }
  }

  test("initFromSql can run twice") {
    val sqlText = java.nio.file.Files.readString(Path.of("conf/init.sql"), StandardCharsets.UTF_8)
    DbInitializer.initFromSql(sqlText)
    DbInitializer.initFromSql(sqlText)

    DB.autoCommit { case given DBSession =>
      val tables = SQL("select name from sqlite_master where type='table'")
        .map(_.string("name"))
        .list
        .apply()
      assert(tables.contains("blogs"))
      assert(tables.contains("tags"))
      assert(tables.contains("blog_tags"))
    }
  }
}
