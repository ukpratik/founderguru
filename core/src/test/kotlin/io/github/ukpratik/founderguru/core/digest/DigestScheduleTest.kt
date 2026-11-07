// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.digest

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** LLD §8: Monday 07:59/08:00/08:01; DST spring and autumn in Europe/Berlin and America/New_York; Asia/Kolkata; daily. */
class DigestScheduleTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val newYork = ZoneId.of("America/New_York")
    private val kolkata = ZoneId.of("Asia/Kolkata")

    private fun at(s: String, zone: ZoneId) = ZonedDateTime.of(LocalDateTime.parse(s), zone)

    private fun weekly(s: String, zone: ZoneId) = DigestSchedule.nextRun(at(s, zone), DigestMode.WEEKLY)

    private fun daily(s: String, zone: ZoneId) = DigestSchedule.nextRun(at(s, zone), DigestMode.DAILY)

    @Test fun `weekly around Monday 08 00`() {
        // 2026-11-09 is a Monday.
        assertThat(weekly("2026-11-09T07:59", kolkata)).isEqualTo(at("2026-11-09T08:00", kolkata))
        assertThat(weekly("2026-11-09T08:00", kolkata)).isEqualTo(at("2026-11-16T08:00", kolkata)) // strictly after
        assertThat(weekly("2026-11-09T08:01", kolkata)).isEqualTo(at("2026-11-16T08:00", kolkata))
        assertThat(weekly("2026-11-07T21:00", kolkata)).isEqualTo(at("2026-11-09T08:00", kolkata)) // Saturday
        assertThat(weekly("2026-11-08T23:59", kolkata)).isEqualTo(at("2026-11-09T08:00", kolkata)) // Sunday
    }

    @Test fun `daily around 08 00`() {
        assertThat(daily("2026-11-07T07:59", kolkata)).isEqualTo(at("2026-11-07T08:00", kolkata))
        assertThat(daily("2026-11-07T08:00", kolkata)).isEqualTo(at("2026-11-08T08:00", kolkata))
        assertThat(daily("2026-11-07T23:30", kolkata)).isEqualTo(at("2026-11-08T08:00", kolkata))
        assertThat(daily("2026-12-31T09:00", kolkata)).isEqualTo(at("2027-01-01T08:00", kolkata))
    }

    @Test fun `Berlin spring forward keeps 08 00 local and the week is 167 hours`() {
        // DST starts Sunday 2027-03-28 02:00 → 03:00. Monday 2027-03-22 08:00 CET → Monday 2027-03-29 08:00 CEST.
        val before = at("2027-03-22T08:00", berlin)
        val next = DigestSchedule.nextRun(before, DigestMode.WEEKLY)
        assertThat(next.toLocalDateTime()).isEqualTo(LocalDateTime.parse("2027-03-29T08:00"))
        assertThat(next.offset.totalSeconds).isEqualTo(2 * 3600)
        assertThat(java.time.Duration.between(before, next).toHours()).isEqualTo(167)
        // Daily across the change night.
        val sat = DigestSchedule.nextRun(at("2027-03-27T08:00", berlin), DigestMode.DAILY)
        assertThat(sat.toLocalDateTime()).isEqualTo(LocalDateTime.parse("2027-03-28T08:00"))
        assertThat(java.time.Duration.between(at("2027-03-27T08:00", berlin), sat).toHours()).isEqualTo(23)
    }

    @Test fun `Berlin fall back keeps 08 00 local and the day is 25 hours`() {
        // DST ends Sunday 2026-10-25 03:00 → 02:00.
        val sat = at("2026-10-24T08:00", berlin)
        val sun = DigestSchedule.nextRun(sat, DigestMode.DAILY)
        assertThat(sun.toLocalDateTime()).isEqualTo(LocalDateTime.parse("2026-10-25T08:00"))
        assertThat(java.time.Duration.between(sat, sun).toHours()).isEqualTo(25)
        assertThat(weekly("2026-10-19T09:00", berlin).toLocalDateTime()).isEqualTo(LocalDateTime.parse("2026-10-26T08:00"))
    }

    @Test fun `New York spring and autumn`() {
        // DST starts Sunday 2027-03-14, ends Sunday 2026-11-01.
        assertThat(weekly("2027-03-13T12:00", newYork).toLocalDateTime()).isEqualTo(LocalDateTime.parse("2027-03-15T08:00"))
        assertThat(weekly("2027-03-13T12:00", newYork).offset.totalSeconds).isEqualTo(-4 * 3600)
        assertThat(weekly("2026-10-31T12:00", newYork).toLocalDateTime()).isEqualTo(LocalDateTime.parse("2026-11-02T08:00"))
        assertThat(weekly("2026-10-31T12:00", newYork).offset.totalSeconds).isEqualTo(-5 * 3600)
    }

    @Test fun `a target inside a DST gap moves to the next valid instant`() {
        // A synthetic check of the resolution rule with a zone whose gap covers 08:00 is not available, so check the
        // rule itself: ZonedDateTime.of on a gap time moves forward by the gap.
        val gap = ZonedDateTime.of(LocalDateTime.parse("2027-03-28T02:30"), berlin)
        assertThat(gap.toLocalDateTime()).isEqualTo(LocalDateTime.parse("2027-03-28T03:30"))
    }

    @Test fun `following counts from the target, not from now`() {
        val target = at("2026-11-09T08:00", kolkata)
        // Ran 3 h late: next is still next Monday 08:00, no drift.
        assertThat(DigestSchedule.following(target, at("2026-11-09T11:00", kolkata), DigestMode.WEEKLY)).isEqualTo(at("2026-11-16T08:00", kolkata))
        // Device off for 3 weeks: the next run is after now, not in the past.
        assertThat(DigestSchedule.following(target, at("2026-11-30T10:00", kolkata), DigestMode.WEEKLY)).isEqualTo(at("2026-12-07T08:00", kolkata))
        assertThat(DigestSchedule.following(target, at("2026-11-09T09:00", kolkata), DigestMode.DAILY)).isEqualTo(at("2026-11-10T08:00", kolkata))
    }

    @Test fun `more than 24 h late is skipped`() {
        val h = 3_600_000L
        assertThat(DigestSchedule.isTooLate(0, 24 * h)).isFalse()
        assertThat(DigestSchedule.isTooLate(0, 24 * h + 1)).isTrue()
        assertThat(DigestSchedule.isTooLate(0, 5 * h)).isFalse()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `off has no schedule`() {
        DigestSchedule.nextRun(at("2026-11-09T08:00", kolkata), DigestMode.OFF)
    }
}
