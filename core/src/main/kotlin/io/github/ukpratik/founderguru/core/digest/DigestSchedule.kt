// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.digest

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** The digest setting (LLD §2.2): stored by name; never rename. A denied notification permission stores OFF (D-017). */
enum class DigestMode { OFF, WEEKLY, DAILY }

/**
 * When the next digest runs (US-012, LLD §4.11): weekly = the first Monday 08:00 strictly after `after`, daily = the
 * first 08:00 strictly after `after`, both in `after`'s zone. Local times are resolved with [ZonedDateTime.of], so a
 * DST gap moves to the next valid instant and an overlap takes the earlier offset.
 */
object DigestSchedule {
    val TIME: LocalTime = LocalTime.of(8, 0)

    /** A run that would fire more than this after its target is skipped (D-023 G-7). */
    val MAX_LATE: Duration = Duration.ofHours(24)

    fun nextRun(after: ZonedDateTime, mode: DigestMode): ZonedDateTime {
        require(mode != DigestMode.OFF) { "no schedule when the digest is off" }
        var date = after.toLocalDate()
        if (mode == DigestMode.WEEKLY) date = date.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
        var candidate = ZonedDateTime.of(date, TIME, after.zone)
        while (!candidate.isAfter(after)) {
            date = if (mode == DigestMode.WEEKLY) date.plusWeeks(1) else date.plusDays(1)
            candidate = ZonedDateTime.of(date, TIME, after.zone)
        }
        return candidate
    }

    /**
     * The run after the one targeted at [target] (LLD §4.11 step 6): counted from the target, not from now, so a late
     * run doesn't drift; if that is already past (the device was off for a long time), the next one after [now].
     */
    fun following(target: ZonedDateTime, now: ZonedDateTime, mode: DigestMode): ZonedDateTime {
        val next = nextRun(target, mode)
        return if (next.isAfter(now)) next else nextRun(now, mode)
    }

    /** True when a run at [now] for [target] is more than 24 h late and must not post. */
    fun isTooLate(targetMs: Long, nowMs: Long): Boolean = nowMs - targetMs > MAX_LATE.toMillis()
}
