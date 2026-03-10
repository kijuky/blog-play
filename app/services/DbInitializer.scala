package services

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import scalikejdbc.DB
import scalikejdbc.SQL

object DbInitializer {
  def initFromFile(path: Path): Unit = {
    val sqlText = Files.readString(path, StandardCharsets.UTF_8)
    initFromSql(sqlText)
  }

  def initFromSql(sqlText: String): Unit = {
    DB.autoCommit { implicit session =>
      // Naive splitting by ';' is sufficient for our simple init file.
      sqlText
        .split(";")
        .map(_.trim)
        .filter(_.nonEmpty)
        .foreach { statement =>
          SQL(statement).execute.apply()
        }
    }
  }
}
