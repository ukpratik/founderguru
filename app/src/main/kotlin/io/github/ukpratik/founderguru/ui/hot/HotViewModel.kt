// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.hot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ukpratik.founderguru.core.feed.Trending
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.StoryRow
import io.github.ukpratik.founderguru.core.tag.Sector
import io.github.ukpratik.founderguru.core.time.Clock
import io.github.ukpratik.founderguru.data.StoryActions
import io.github.ukpratik.founderguru.data.Tab
import io.github.ukpratik.founderguru.platform.Connectivity
import io.github.ukpratik.founderguru.data.prefs.SettingsStore
import io.github.ukpratik.founderguru.ui.feed.FeedHeader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HotUiState {
    data object Loading : HotUiState

    /** [rank] is 1-based ("This week's no. 1 sector"); [offlineSince] is set while offline. */
    data class Content(
        val header: FeedHeader,
        val sector: Sector,
        val rank: Int,
        val count: Int,
        val rows: List<StoryRow>,
        val offlineSince: Long?,
        val offline: Boolean,
    ) : HotUiState

    /** The sector no longer makes the strip after a refresh (design state 02). */
    data class NotQualifying(val header: FeedHeader, val sector: Sector, val offlineSince: Long?, val offline: Boolean) : HotUiState
}

/** What's hot results (US-006, SCR-05): the tab's 7-day stories in [sector], newest first; the lens is ignored. */
@OptIn(ExperimentalCoroutinesApi::class)
class HotViewModel(
    private val tab: Tab,
    private val sector: Sector,
    settings: SettingsStore,
    window: (Tab, Region) -> Flow<List<StoryRow>>,
    connectivity: Connectivity,
    private val actions: StoryActions,
    private val clock: Clock,
) : ViewModel() {
    private val rows = settings.settings.map { it.region }.distinctUntilChanged().flatMapLatest { region ->
        if (region == null) flowOf(emptyList()) else window(tab, region)
    }

    val uiState: StateFlow<HotUiState> = combine(settings.settings, rows, connectivity.isOnline) { s, rows, online ->
        HotStateMapper.map(FeedHeader(tab, s.region ?: Region.GLOBAL), sector, rows, online, s.lastRefreshAt, clock.now())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HotUiState.Loading)

    fun setSaved(row: StoryRow, saved: Boolean) {
        viewModelScope.launch { actions.setSaved(row.id, saved) }
    }

    fun markOpened(row: StoryRow) {
        viewModelScope.launch { actions.markOpened(row.id) }
    }
}

/** Pure state choice for the results screen (JVM-tested). */
object HotStateMapper {
    fun map(header: FeedHeader, sector: Sector, rows: List<StoryRow>, online: Boolean, lastRefreshAt: Long?, now: Long): HotUiState {
        val strip = Trending.compute(rows, now)
        val index = strip.indexOfFirst { it.sector == sector }
        val offlineSince = if (online) null else lastRefreshAt
        return if (index < 0) {
            HotUiState.NotQualifying(header, sector, offlineSince, !online)
        } else {
            HotUiState.Content(header, sector, index + 1, strip[index].count, Trending.results(rows, sector, now), offlineSince, !online)
        }
    }
}
