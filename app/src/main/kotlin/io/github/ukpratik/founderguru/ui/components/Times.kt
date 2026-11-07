// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.components

import android.content.Context
import android.content.res.Resources
import android.text.format.DateFormat
import io.github.ukpratik.founderguru.R
import io.github.ukpratik.founderguru.core.time.RelativeTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Time copy (design-system §7): short forms on screen, spoken forms for TalkBack (plurals). */
object Times {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val locale: Locale get() = Locale.getDefault()

    fun short(res: Resources, at: Long, now: Long): String = when (val t = RelativeTime.of(at, now, zone)) {
        is RelativeTime.Minutes -> if (t.n == 0) res.getString(R.string.time_now) else res.getString(R.string.time_minutes_short, t.n)
        is RelativeTime.Hours -> res.getString(R.string.time_hours_short, t.n)
        RelativeTime.Yesterday -> res.getString(R.string.time_yesterday)
        is RelativeTime.Weekday -> t.day.getDisplayName(TextStyle.SHORT, locale)
        is RelativeTime.Date -> t.date.format(DateTimeFormatter.ofPattern("d MMM", locale))
    }

    fun spoken(res: Resources, at: Long, now: Long): String = when (val t = RelativeTime.of(at, now, zone)) {
        is RelativeTime.Minutes -> if (t.n == 0) res.getString(R.string.time_now_spoken) else res.getQuantityString(R.plurals.time_minutes_ago, t.n, t.n)
        is RelativeTime.Hours -> res.getQuantityString(R.plurals.time_hours_ago, t.n, t.n)
        RelativeTime.Yesterday -> res.getString(R.string.time_yesterday_spoken)
        is RelativeTime.Weekday -> t.day.getDisplayName(TextStyle.FULL, locale)
        is RelativeTime.Date -> t.date.format(DateTimeFormatter.ofPattern("d MMMM", locale))
    }

    /** "08:10" in the user's 12/24-hour setting. */
    fun clock(context: Context, at: Long): String {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return Instant.ofEpochMilli(at).atZone(zone).format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    /** The last-visit time in markers: "08:10", "yesterday at 21:40", "Thu at 21:40", "12 Oct at 21:40". */
    fun visit(context: Context, at: Long, now: Long): String {
        val res = context.resources
        val time = clock(context, at)
        val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(day, Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
        return when {
            days <= 0 -> time
            days == 1L -> res.getString(R.string.time_yesterday_at, time)
            days < 7 -> res.getString(R.string.time_day_at, day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale), time)
            else -> res.getString(R.string.time_day_at, day.format(DateTimeFormatter.ofPattern("d MMM", locale)), time)
        }
    }

    /** The dateline date: "Saturday, 7 November". */
    fun dateline(now: Long): String =
        Instant.ofEpochMilli(now).atZone(zone).format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
}
