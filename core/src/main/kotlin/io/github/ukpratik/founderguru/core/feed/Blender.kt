// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.feed

import io.github.ukpratik.founderguru.core.model.StoryRow

/**
 * Local = the region's stories plus a capped share of Global stories that mention the region (US-002, D-010, D-017,
 * LLD §4.8). The cap is strict: `maxVia = floor(3 × |R| / 7)` keeps `via / (R + via) ≤ 30%`, and an empty region gets
 * no via stories at all. Computed on the unfiltered 14-day window, before any lens filter.
 */
object Blender {
    /** [regional] and [viaCandidates] may come in any order; the result is newest first. */
    fun blend(regional: List<StoryRow>, viaCandidates: List<StoryRow>): List<StoryRow> {
        val maxVia = maxVia(regional.size)
        val via = viaCandidates.sortedByDescending { it.publishedAt }.take(maxVia).map { it.copy(viaGlobal = true) }
        return (regional + via).sortedWith(NEWEST_FIRST)
    }

    fun maxVia(regionalCount: Int): Int = 3 * regionalCount / 7

    /** Newest first; ties broken by id (descending) so the order is stable across refreshes. */
    val NEWEST_FIRST: Comparator<StoryRow> = compareByDescending<StoryRow> { it.publishedAt }.thenByDescending { it.id }
}
