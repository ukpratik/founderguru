// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.feed

import io.github.ukpratik.founderguru.core.model.StoryRow
import io.github.ukpratik.founderguru.core.tag.Sector
import java.util.Locale

data class SectorCount(val sector: Sector, val count: Int)

/**
 * "What's hot this week" (US-006, LLD §4.7), a pure, deterministic function. Input: the tab's rows after blending and
 * before the lens filter. A sector qualifies with ≥ [MIN_STORIES] stories from ≥ [MIN_SOURCES] distinct sources in the
 * last 7 days; the top [MAX_CHIPS] by count are shown, ties broken A–Z by display name. Fewer than [MIN_CHIPS]
 * qualifying sectors → empty (the strip is hidden).
 */
object Trending {
    const val WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    const val MIN_STORIES = 3
    const val MIN_SOURCES = 2
    const val MAX_CHIPS = 6
    const val MIN_CHIPS = 3

    fun compute(rows: List<StoryRow>, now: Long): List<SectorCount> {
        val recent = recentRows(rows, now)
        val qualifying = Sector.entries.mapNotNull { sector ->
            val hits = recent.filter { it.sectors and sector.bit != 0L }
            if (hits.size >= MIN_STORIES && hits.map { it.sourceId }.distinct().size >= MIN_SOURCES) SectorCount(sector, hits.size) else null
        }.sortedWith(compareByDescending<SectorCount> { it.count }.thenBy { it.sector.displayName.lowercase(Locale.ROOT) })
        return if (qualifying.size < MIN_CHIPS) emptyList() else qualifying.take(MAX_CHIPS)
    }

    /** The What's-hot results screen: the same 7-day rows with [sector]'s bit, newest first. */
    fun results(rows: List<StoryRow>, sector: Sector, now: Long): List<StoryRow> =
        recentRows(rows, now).filter { it.sectors and sector.bit != 0L }.sortedWith(Blender.NEWEST_FIRST)

    private fun recentRows(rows: List<StoryRow>, now: Long) = rows.filter { it.publishedAt >= now - WINDOW_MS }
}
