package services

import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.SQL

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object DbInitializer {
  def initFromFile(path: Path): Unit = {
    val sqlText = Files.readString(path, StandardCharsets.UTF_8)
    initFromSql(sqlText)
  }

  def initFromSql(sqlText: String): Unit = {
    DB.autoCommit { case given DBSession =>
      // Naive splitting by ';' is sufficient for our simple init file.
      sqlText
        .split(";")
        .map(_.trim)
        .filter(_.nonEmpty)
        .foreach { SQL(_).execute.apply() }
    }
  }
}
