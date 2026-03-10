package services

import java.nio.charset.StandardCharsets
import java.nio.file.Path

import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import scalikejdbc.ConnectionPool
import scalikejdbc.DB
import scalikejdbc.SQL

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

    DB.autoCommit { implicit session =>
      val tables = SQL("select name from sqlite_master where type='table'")
        .map(_.string("name"))
        .list
        .apply()

      assert(tables.contains("posts"))
      assert(tables.contains("tags"))
      assert(tables.contains("post_tags"))
    }
  }
}
