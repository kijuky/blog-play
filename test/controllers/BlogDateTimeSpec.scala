package controllers

import org.scalatest.funsuite.AnyFunSuite

import java.time.OffsetDateTime
import java.time.ZoneId

class BlogDateTimeSpec extends AnyFunSuite {
  test("format uses default pattern when rawPattern is None") {
    val dateTime = BlogDateTime.from(Some("Asia/Tokyo"), None)
    val value = OffsetDateTime.parse("2026-03-10T00:00:00Z")
    assert(dateTime.format(Some(value)) == "2026/03/10 09:00")
  }

  test("format uses provided pattern") {
    val dateTime = BlogDateTime.from(Some("UTC"), Some("yyyy-MM-dd HH:mm"))
    val value = OffsetDateTime.parse("2026-03-10T00:00:00Z")
    assert(dateTime.format(Some(value)) == "2026-03-10 00:00")
  }

  test("format returns empty string for None") {
    val dateTime = BlogDateTime.from(None, None)
    assert(dateTime.format(None).isEmpty)
  }

  test("invalid zone id throws") {
    assertThrows[Throwable] {
      BlogDateTime.from(Some("Invalid/Zone"), None)
    }
  }

  test("invalid pattern throws") {
    assertThrows[Throwable] {
      BlogDateTime.from(Some("UTC"), Some("yyyy-MM-dd HH:mm '"))
    }
  }
}
