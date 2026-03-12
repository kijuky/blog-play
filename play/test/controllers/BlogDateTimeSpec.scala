package controllers

import org.scalatest.funsuite.AnyFunSuite

import java.time.OffsetDateTime
import java.time.ZoneId

class BlogDateTimeSpec extends AnyFunSuite {
  test("format uses default pattern when rawPattern is None") {
    val dateTime = BlogDateTime.from(ZoneId.of("Asia/Tokyo"), None)
    val value = OffsetDateTime.parse("2026-03-10T00:00:00Z")
    assert(dateTime.format(Some(value)) == "2026/03/10 09:00")
  }

  test("format uses provided pattern") {
    val dateTime = BlogDateTime.from(ZoneId.of("UTC"), Some("yyyy-MM-dd HH:mm"))
    val value = OffsetDateTime.parse("2026-03-10T00:00:00Z")
    assert(dateTime.format(Some(value)) == "2026-03-10 00:00")
  }

  test("invalid pattern throws") {
    assertThrows[Throwable] {
      BlogDateTime.from(ZoneId.of("UTC"), Some("yyyy-MM-dd HH:mm '"))
    }
  }
}
