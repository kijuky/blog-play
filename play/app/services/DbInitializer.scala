package services

import scalikejdbc.DB
import scalikejdbc.DBSession
import scalikejdbc.SQL

import java.net.URL
import scala.io.Source
import scala.util.Using

object DbInitializer {
  def initFromResource(sqlUrl: URL): Unit = {
    Using(Source.fromURL(sqlUrl))(_.getLines.mkString("\n"))
      .foreach(initFromSql)
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
