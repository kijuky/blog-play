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
  private val defaultZoneId = ZoneId.systemDefault()
  private val defaultFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

  def from(rawZoneId: Option[String], rawPattern: Option[String]): BlogDateTime = {
    val zoneId = rawZoneId.map(ZoneId.of).getOrElse(defaultZoneId)
    val fmt = rawPattern.map(DateTimeFormatter.ofPattern).getOrElse(defaultFormatter)
    new BlogDateTime(zoneId, fmt)
  }
}
