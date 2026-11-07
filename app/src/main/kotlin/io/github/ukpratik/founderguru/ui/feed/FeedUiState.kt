// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.feed

import io.github.ukpratik.founderguru.core.feed.Banner
import io.github.ukpratik.founderguru.core.feed.FeedAssembler
import io.github.ukpratik.founderguru.core.feed.FeedSections
import io.github.ukpratik.founderguru.core.feed.FeedState
import io.github.ukpratik.founderguru.core.feed.LensFilter
import io.github.ukpratik.founderguru.core.feed.SectorCount
import io.github.ukpratik.founderguru.core.feed.Trending
import io.github.ukpratik.founderguru.core.model.FailureKind
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.StoryRow
import io.github.ukpratik.founderguru.data.Tab
import io.github.ukpratik.founderguru.data.db.SourceStateEntity
import io.github.ukpratik.founderguru.data.prefs.Settings

/** Whose feed this is: the tab, the user's region and whether they chose "Global only" (US-001, D-016). */
data class FeedHeader(val tab: Tab, val region: Region) {
    val globalOnly: Boolean get() = region == Region.GLOBAL

    /** The region whose sources fill this tab. */
    val tabRegion: Region get() = if (tab == Tab.GLOBAL || globalOnly) Region.GLOBAL else region
}

/** The dateline's right-hand status (design `home-local.html`). */
sealed interface DatelineStatus {
    /** First sync: "Getting stories from 11 sources…". */
    data class Getting(val sources: Int) : DatelineStatus
    data object Refreshing : DatelineStatus

    /** "Updated 6 min ago"; offline: "Updated 08:10". Null = never. */
    data class Updated(val at: Long?, val offline: Boolean) : DatelineStatus
}

enum class EmptyReason { NO_STORIES_IN_REGION, LENS_EMPTY, ALL_SOURCES_OFF }

/** One feed tab's screen (US-011, LLD §5). Strings are resolved in the UI; the state holds data only. */
sealed interface FeedUiState {
    data object Loading : FeedUiState

    data class FirstSync(val header: FeedHeader, val dateline: DatelineStatus, val lens: LensFilter) : FeedUiState

    data class FirstSyncOffline(val header: FeedHeader) : FeedUiState

    data class Content(
        val header: FeedHeader,
        val dateline: DatelineStatus,
        val lens: LensFilter,
        val hot: List<SectorCount>,
        val sections: FeedSections,
        val banners: List<Banner>,
        val isRefreshing: Boolean,
    ) : FeedUiState

    data class Empty(
        val header: FeedHeader,
        val dateline: DatelineStatus,
        val reason: EmptyReason,
        val lens: LensFilter,
        val hot: List<SectorCount>,
        val banners: List<Banner>,
        /** The tab's enabled source count, for "Its 3 sources have posted nothing…". */
        val sourceCount: Int,
        val isRefreshing: Boolean,
    ) : FeedUiState

    /** Every source of the tab failed and nothing is cached. */
    data class AllFailed(val header: FeedHeader, val sourceCount: Int) : FeedUiState {
        val canSeeGlobal: Boolean get() = header.tabRegion != Region.GLOBAL
    }
}

/** Everything one feed state is computed from (all plain values, so the mapping is a pure, JVM-tested function). */
data class FeedInputs(
    val tab: Tab,
    val settings: Settings,
    val rows: List<StoryRow>,
    val sources: List<SourceStateEntity>,
    val isOnline: Boolean,
    val isRefreshing: Boolean,
    /** The frozen last visit of this visit (null = first visit). */
    val anchor: Long?,
    val now: Long,
)

object FeedStateMapper {
    private val FAILURES = FailureKind.entries.map { it.name }.toSet()
    private val LIMITED = setOf(Region.SEA, Region.LATAM)

    fun map(i: FeedInputs): FeedUiState {
        val header = FeedHeader(i.tab, i.settings.region ?: Region.GLOBAL)
        val tabSources = i.sources.filter { it.region == header.tabRegion.name && it.enabled }
        val failed = tabSources.filter { it.lastStatus in FAILURES }
        val lens = i.settings.lens(i.tab)
        val sections = FeedAssembler.assemble(i.rows, lens, i.anchor)
        val input = FeedAssembler.StateInput(
            windowRows = i.rows.size,
            filteredRows = sections.new.size + sections.earlier.size,
            lastRefreshAt = i.settings.lastRefreshAt,
            isOnline = i.isOnline,
            enabledSources = tabSources.size,
            failedSources = failed.size,
            refreshedAtLeastOnce = tabSources.any { it.lastAttemptAt != null },
        )
        val limited = i.tab == Tab.LOCAL && header.region in LIMITED && header.region !in i.settings.limitedBannerDismissed
        val banners = FeedAssembler.banners(input, offlineSince = i.settings.lastRefreshAt, limitedCoverage = limited)
            .filter { b -> b !is Banner.SourcesFailed || (failed.maxOfOrNull { it.lastAttemptAt ?: 0 } ?: 0) > i.settings.failedNoteDismissedAt }
            .sortedBy { order(it) }
        val dateline = when {
            i.isRefreshing -> DatelineStatus.Refreshing
            else -> DatelineStatus.Updated(i.settings.lastRefreshAt, offline = !i.isOnline)
        }
        val hot = Trending.compute(i.rows, i.now)
        var state = FeedAssembler.chooseState(input)
        // A refresh that can fill an empty tab shows the skeleton, not a stale empty or error state.
        if (i.isRefreshing && i.rows.isEmpty() && state in setOf(FeedState.EMPTY_REGION, FeedState.ALL_FAILED, FeedState.FIRST_SYNC_OFFLINE)) {
            state = FeedState.FIRST_SYNC
        }
        val refreshCount = i.sources.count { it.enabled && (it.region == header.tabRegion.name || it.region == Region.GLOBAL.name) }
        return when (state) {
            FeedState.FIRST_SYNC -> FeedUiState.FirstSync(header, DatelineStatus.Getting(refreshCount), lens)
            FeedState.FIRST_SYNC_OFFLINE -> FeedUiState.FirstSyncOffline(header)
            FeedState.ALL_FAILED -> FeedUiState.AllFailed(header, tabSources.size)
            FeedState.CONTENT -> FeedUiState.Content(header, dateline, lens, hot, sections, banners, i.isRefreshing)
            FeedState.EMPTY_LENS -> empty(header, dateline, EmptyReason.LENS_EMPTY, lens, hot, banners, tabSources.size, i.isRefreshing)
            FeedState.EMPTY_REGION -> empty(header, dateline, EmptyReason.NO_STORIES_IN_REGION, lens, emptyList(), banners, tabSources.size, i.isRefreshing)
            FeedState.ALL_SOURCES_OFF -> empty(header, dateline, EmptyReason.ALL_SOURCES_OFF, lens, emptyList(), emptyList(), 0, i.isRefreshing)
        }
    }

    @Suppress("LongParameterList")
    private fun empty(
        header: FeedHeader, dateline: DatelineStatus, reason: EmptyReason, lens: LensFilter, hot: List<SectorCount>,
        banners: List<Banner>, sources: Int, refreshing: Boolean,
    ) = FeedUiState.Empty(header, dateline, reason, lens, hot, banners, sources, refreshing)

    /** Offline strip (pinned) → Limited coverage → sources failed (design notes). */
    private fun order(b: Banner) = when (b) {
        is Banner.Offline -> 0
        Banner.LimitedCoverage -> 1
        is Banner.SourcesFailed -> 2
    }
}
