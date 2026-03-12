package services

import play.api.Logger
import scalikejdbc.GlobalSettings

object SqlLogging {
  private val logger = Logger(getClass)

  // Keep logs single-line and low-noise:
  // - Avoid printing bound params (they may include large HTML bodies).
  // - Include first relevant app stack frame for quick navigation.
  def install(maxSqlChars: Int = 240): Unit = {
    GlobalSettings.queryCompletionListener = (sql, _, _) => {
      val stack = bestFrame(Exception().getStackTrace)
      logger.info(formatLine("OK", sql, None, stack, maxSqlChars))
    }

    GlobalSettings.queryFailureListener = (sql, _, cause) => {
      val stack = bestFrame(cause.getStackTrace)
      logger.error(
        formatLine("NG", sql, Option(cause.getMessage), stack, maxSqlChars)
      )
    }
  }

  private def formatLine(
    status: String,
    sql: String,
    message: Option[String],
    stack: Option[String],
    maxSqlChars: Int
  ): String = {
    val cleanedSql = oneLine(sql).take(maxSqlChars)
    val msg =
      message
        .filter(_.nonEmpty)
        .map(m => s" msg=${oneLine(m).take(120)}")
        .getOrElse("")
    val where = stack.map(" at=" + _).getOrElse("")
    s"SQL $status sql=$cleanedSql$msg$where"
  }

  private def oneLine(s: String): String =
    s.replaceAll("\\s+", " ").trim

  private def bestFrame(stack: Array[StackTraceElement]): Option[String] = {
    // Prefer app frames; fall back to first non-library frame.
    val preferred =
      stack
        .find(e =>
          e.getClassName.startsWith("controllers.") ||
            e.getClassName.startsWith("services.") ||
            e.getClassName.startsWith("loader.")
        )
        .orElse(stack.find(e => !isNoise(e.getClassName)))

    preferred.map { e =>
      val cls = e.getClassName
      val m = e.getMethodName
      val file = Option(e.getFileName).getOrElse("?")
      val line = e.getLineNumber
      s"$cls.$m($file:$line)"
    }
  }

  private def isNoise(className: String): Boolean = {
    className.startsWith("java.") ||
    className.startsWith("jdk.") ||
    className.startsWith("sun.") ||
    className.startsWith("scala.") ||
    className.startsWith("play.") ||
    className.startsWith("scalikejdbc.") ||
    className.startsWith("org.slf4j.") ||
    className.startsWith("ch.qos.logback.") ||
    className.startsWith("services.SqlLogging")
  }
}
