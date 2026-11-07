// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.feed

import io.github.ukpratik.founderguru.core.model.StoryDraft
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Per-source daily cap (US-003, LLD §2.3): keeps at most [cap] items per local day, in feed order, counting what's
 * already stored. Pass only drafts that aren't stored yet, or re-fetched items would use up the day's quota.
 */
object DailyCap {
    fun apply(drafts: List<StoryDraft>, cap: Int?, existingPerDay: Map<LocalDate, Int>, zone: ZoneId): List<StoryDraft> {
        if (cap == null) return drafts
        val counts = HashMap(existingPerDay)
        return drafts.filter { draft ->
            val day = Instant.ofEpochMilli(draft.publishedAt).atZone(zone).toLocalDate()
            val n = counts.getOrDefault(day, 0)
            (n < cap).also { if (it) counts[day] = n + 1 }
        }
    }

    fun perDay(publishedAt: List<Long>, zone: ZoneId): Map<LocalDate, Int> =
        publishedAt.groupingBy { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.eachCount()
}
