package controllers

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

final class BlogDateTime(zoneId: ZoneId, fmt: DateTimeFormatter) {
  def format(value: Option[OffsetDateTime]): String = {
    value
      .map(_.atZoneSameInstant(zoneId).format(fmt))
      .getOrElse("")
  }
}

object BlogDateTime {
  private val defaultFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

  def from(zoneId: ZoneId, rawPattern: Option[String]): BlogDateTime = {
    val fmt =
      rawPattern.map(DateTimeFormatter.ofPattern).getOrElse(defaultFormatter)
    BlogDateTime(zoneId, fmt)
  }
}
