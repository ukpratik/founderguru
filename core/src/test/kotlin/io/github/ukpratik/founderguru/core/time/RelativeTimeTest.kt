// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.time

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RelativeTimeTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = ZonedDateTime.of(2026, 11, 7, 8, 12, 0, 0, zone) // Saturday
    private fun at(t: ZonedDateTime) = RelativeTime.of(t.toInstant().toEpochMilli(), now.toInstant().toEpochMilli(), zone)

    @Test fun `minutes and hours`() {
        assertThat(at(now)).isEqualTo(RelativeTime.Minutes(0))
        assertThat(at(now.minusMinutes(42))).isEqualTo(RelativeTime.Minutes(42))
        assertThat(at(now.minusMinutes(60))).isEqualTo(RelativeTime.Hours(1))
        assertThat(at(now.minusHours(8))).isEqualTo(RelativeTime.Hours(8))
        // Late yesterday but under 24 h: still hours.
        assertThat(at(now.minusHours(10))).isEqualTo(RelativeTime.Hours(10))
    }

    @Test fun `yesterday, weekday, date`() {
        assertThat(at(now.minusHours(30))).isEqualTo(RelativeTime.Yesterday)
        assertThat(at(now.minusDays(2))).isEqualTo(RelativeTime.Weekday(DayOfWeek.THURSDAY))
        assertThat(at(now.minusDays(6))).isEqualTo(RelativeTime.Weekday(DayOfWeek.SUNDAY))
        assertThat(at(now.minusDays(7))).isEqualTo(RelativeTime.Date(LocalDate.of(2026, 10, 31)))
    }

    @Test fun `future times count as now`() {
        assertThat(at(now.plusMinutes(5))).isEqualTo(RelativeTime.Minutes(0))
    }
}
