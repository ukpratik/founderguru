// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.parse

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Lenient feed date parsing (LLD §4.3): RFC 822/1123 first (with pre-normalising), then ISO 8601. Returns epoch ms (UTC),
 * or null when nothing parses; the caller then falls back to `firstSeenAt`.
 */
object FeedDates {
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private val ZONES: Map<String, Int> = mapOf(
        "GMT" to 0, "UT" to 0, "UTC" to 0, "Z" to 0,
        "EST" to -5 * 60, "EDT" to -4 * 60, "CST" to -6 * 60, "CDT" to -5 * 60,
        "MST" to -7 * 60, "MDT" to -6 * 60, "PST" to -8 * 60, "PDT" to -7 * 60,
        "IST" to 5 * 60 + 30, "CET" to 60, "CEST" to 2 * 60, "WAT" to 60, "EAT" to 3 * 60, "SAST" to 2 * 60,
    )

    // [weekday[,]] d Mon yy[yy] h:mm[:ss] [zone]
    private val RFC822 = Regex(
        "^(?:[A-Za-z]+,?\\s+)?(\\d{1,2})\\s+([A-Za-z]{3,9})\\.?\\s+(\\d{2,4})\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?:\\s*([A-Za-z]{1,4}|[+-]\\d{2}:?\\d{2}))?$",
    )

    private val ISO = Regex(
        "^(\\d{4})-(\\d{2})-(\\d{2})(?:[Tt ](\\d{2}):(\\d{2})(?::(\\d{2})(?:[.,]\\d+)?)?)?\\s*([Zz]|[+-]\\d{2}(?::?\\d{2})?)?$",
    )

    fun parse(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim().replace(Regex("\\s+"), " ")
        return parseRfc822(s) ?: parseIso(s)
    }

    private fun parseRfc822(s: String): Long? {
        val m = RFC822.matchEntire(s) ?: return null
        val (d, mon, y, hh, mm) = m.destructured
        val ss = m.groupValues[6]
        val zone = m.groupValues[7]
        val month = MONTHS.indexOf(mon.take(3).lowercase()) + 1
        if (month == 0) return null
        var year = y.toInt()
        if (y.length == 2) year += if (year < 50) 2000 else 1900 else if (y.length == 3) return null
        val offsetMinutes = when {
            zone.isEmpty() -> 0
            zone[0] == '+' || zone[0] == '-' -> numericOffset(zone) ?: return null
            else -> ZONES[zone.uppercase()] ?: return null
        }
        return toEpoch(year, month, d.toInt(), hh.toInt(), mm.toInt(), ss.toIntOrNull() ?: 0, offsetMinutes)
    }

    private fun parseIso(s: String): Long? {
        val m = ISO.matchEntire(s) ?: return null
        val g = m.groupValues
        val zone = g[7]
        val offsetMinutes = when {
            zone.isEmpty() || zone == "Z" || zone == "z" -> 0
            else -> numericOffset(zone) ?: return null
        }
        return toEpoch(
            g[1].toInt(), g[2].toInt(), g[3].toInt(),
            g[4].toIntOrNull() ?: 0, g[5].toIntOrNull() ?: 0, g[6].toIntOrNull() ?: 0, offsetMinutes,
        )
    }

    /** `+0530`, `+05:30`, `-08` → minutes. */
    private fun numericOffset(z: String): Int? {
        val sign = if (z[0] == '-') -1 else 1
        val digits = z.substring(1).replace(":", "")
        val h = digits.take(2).toIntOrNull() ?: return null
        val min = if (digits.length > 2) digits.substring(2).toIntOrNull() ?: return null else 0
        if (h > 18 || min > 59) return null
        return sign * (h * 60 + min)
    }

    private fun toEpoch(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int, offsetMinutes: Int): Long? = try {
        // 24:00 and leap seconds (60) are clamped rather than rejected.
        LocalDateTime.of(y, mo, d, h.coerceAtMost(23), mi, s.coerceAtMost(59))
            .toInstant(ZoneOffset.ofTotalSeconds(offsetMinutes * 60))
            .toEpochMilli()
    } catch (_: DateTimeException) {
        null
    }
}
