// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.feed

import io.github.ukpratik.founderguru.core.model.Lens
import io.github.ukpratik.founderguru.core.model.StoryRow

/** The lens chip selection (US-005): All, or one lens. */
sealed interface LensFilter {
    data object All : LensFilter
    data class Only(val lens: Lens) : LensFilter

    fun accepts(row: StoryRow): Boolean = this is All || row.lenses and (this as Only).lens.bit != 0
}

/** Where the catch-up marker goes (US-008, design `home-local.html`, D-023 G-1). */
enum class CaughtUpMarker {
    /** First visit (no stored last visit): no divider, one list, "You're caught up" at the end. */
    END_OF_LIST,

    /** Something is new: "New since your last visit · n", the new stories, then "You're caught up". */
    AFTER_NEW,

    /** Nothing is new: "Nothing new since <anchor>" at the top (G-1). */
    TOP_NOTHING_NEW,
}

data class FeedSections(
    val lens: LensFilter,
    val new: List<StoryRow>,
    val earlier: List<StoryRow>,
    val marker: CaughtUpMarker,
    /** The last-visit time the sections were cut at (null on a first visit). */
    val anchor: Long?,
) {
    val newCount: Int get() = new.size
    val isEmpty: Boolean get() = new.isEmpty() && earlier.isEmpty()
}

/** The screen states of a feed tab (US-011, LLD §5). */
enum class FeedState { FIRST_SYNC, FIRST_SYNC_OFFLINE, CONTENT, EMPTY_REGION, EMPTY_LENS, ALL_SOURCES_OFF, ALL_FAILED }

/** Banners shown above Content/Empty (US-011, US-002). */
sealed interface Banner {
    data class Offline(val since: Long?) : Banner
    data class SourcesFailed(val count: Int) : Banner
    data object LimitedCoverage : Banner
}

/** Pure sectioning and state choice for a feed tab (LLD §4.9, §5). */
object FeedAssembler {

    /** [rows] are the tab's window (already blended for Local); [anchor] is the frozen last visit, null on a first visit. */
    fun assemble(rows: List<StoryRow>, lens: LensFilter, anchor: Long?): FeedSections {
        val filtered = rows.filter(lens::accepts).sortedWith(Blender.NEWEST_FIRST)
        if (anchor == null) return FeedSections(lens, emptyList(), filtered, CaughtUpMarker.END_OF_LIST, null)
        val (new, earlier) = filtered.partition { it.firstSeenAt > anchor }
        val marker = if (new.isEmpty()) CaughtUpMarker.TOP_NOTHING_NEW else CaughtUpMarker.AFTER_NEW
        return FeedSections(lens, new, earlier, marker, anchor)
    }

    /**
     * Inputs for [chooseState]. [windowRows] is the tab's unfiltered window; [enabledSources] / [failedSources] count the
     * tab's sources (region or Global); [lastRefreshAt] is the last refresh with at least one success.
     */
    data class StateInput(
        val windowRows: Int,
        val filteredRows: Int,
        val lastRefreshAt: Long?,
        val isOnline: Boolean,
        val enabledSources: Int,
        val failedSources: Int,
        val refreshedAtLeastOnce: Boolean,
    )

    /** Every US-011 row as a pure decision. */
    fun chooseState(input: StateInput): FeedState = with(input) {
        when {
            enabledSources == 0 -> FeedState.ALL_SOURCES_OFF
            windowRows > 0 -> if (filteredRows == 0) FeedState.EMPTY_LENS else FeedState.CONTENT
            // Nothing cached from here on.
            lastRefreshAt == null && !isOnline -> FeedState.FIRST_SYNC_OFFLINE
            refreshedAtLeastOnce && failedSources >= enabledSources -> FeedState.ALL_FAILED
            lastRefreshAt == null -> FeedState.FIRST_SYNC
            else -> FeedState.EMPTY_REGION
        }
    }

    /** Banners for Content and the empty states: offline (with cached stories), some sources failed. */
    fun banners(input: StateInput, offlineSince: Long?, limitedCoverage: Boolean): List<Banner> = buildList {
        if (!input.isOnline && input.windowRows > 0) add(Banner.Offline(offlineSince ?: input.lastRefreshAt))
        if (input.failedSources in 1 until input.enabledSources) add(Banner.SourcesFailed(input.failedSources))
        if (limitedCoverage) add(Banner.LimitedCoverage)
    }
}
