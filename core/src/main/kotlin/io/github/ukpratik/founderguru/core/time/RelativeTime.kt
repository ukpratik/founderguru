// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Story times as the design writes them (design-system §7): "42m", "3h", "Yesterday", a weekday within 7 days, then a
 * date. The app turns each case into short text and a spoken form ("42 minutes ago") with plurals resources.
 */
sealed interface RelativeTime {
    /** Under an hour; 0 means "just now". */
    data class Minutes(val n: Int) : RelativeTime
    data class Hours(val n: Int) : RelativeTime
    data object Yesterday : RelativeTime
    data class Weekday(val day: DayOfWeek) : RelativeTime
    data class Date(val date: LocalDate) : RelativeTime

    companion object {
        private const val MINUTE = 60_000L
        private const val HOUR = 60 * MINUTE

        fun of(at: Long, now: Long, zone: ZoneId): RelativeTime {
            val age = (now - at).coerceAtLeast(0)
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
            val days = ChronoUnit.DAYS.between(day, today)
            return when {
                age < HOUR -> Minutes((age / MINUTE).toInt())
                days <= 0L -> Hours((age / HOUR).toInt())
                days == 1L -> if (age < 24 * HOUR) Hours((age / HOUR).toInt()) else Yesterday
                days < 7L -> Weekday(day.dayOfWeek)
                else -> Date(day)
            }
        }
    }
}
