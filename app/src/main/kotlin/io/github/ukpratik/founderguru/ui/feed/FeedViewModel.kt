// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ukpratik.founderguru.core.feed.Banner
import io.github.ukpratik.founderguru.core.feed.LensFilter
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.StoryRow
import io.github.ukpratik.founderguru.core.time.Clock
import io.github.ukpratik.founderguru.data.Refresher
import io.github.ukpratik.founderguru.data.StoryActions
import io.github.ukpratik.founderguru.data.Tab
import io.github.ukpratik.founderguru.data.Visit
import io.github.ukpratik.founderguru.data.db.SourceStateEntity
import io.github.ukpratik.founderguru.data.prefs.Settings
import io.github.ukpratik.founderguru.data.prefs.SettingsStore
import io.github.ukpratik.founderguru.platform.Connectivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One feed tab (US-002, US-003, US-005, US-006, US-008, US-011; LLD §5): a single [uiState] built from settings, the
 * tab's story window, source health, connectivity, the refresh flag and the frozen visit anchor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModel(
    private val tab: Tab,
    private val settings: SettingsStore,
    window: (Tab, Region) -> Flow<List<StoryRow>>,
    sources: Flow<List<SourceStateEntity>>,
    private val connectivity: Connectivity,
    private val refresher: Refresher,
    visits: Flow<Visit?>,
    private val actions: StoryActions,
    private val clock: Clock,
) : ViewModel() {

    private val rows: Flow<List<StoryRow>> = settings.settings.map { it.region }.distinctUntilChanged().flatMapLatest { region ->
        if (region == null) flowOf(emptyList()) else window(tab, region)
    }

    private data class Live(val settings: Settings, val rows: List<StoryRow>, val sources: List<SourceStateEntity>, val online: Boolean, val refreshing: Boolean)

    private val anchor: Flow<Long?> = visits.filter { it?.tab == tab }.map { it?.anchor }

    val uiState: StateFlow<FeedUiState> = combine(
        combine(settings.settings, rows, sources, connectivity.isOnline, refresher.refreshing, ::Live),
        anchor,
    ) { live, anchor ->
        FeedStateMapper.map(FeedInputs(tab, live.settings, live.rows, live.sources, live.online, live.refreshing, anchor, clock.now()))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FeedUiState.Loading)

    init {
        // First sync (US-001: "the first refresh starts"), and again when the connection returns before it succeeded.
        viewModelScope.launch {
            combine(settings.settings, connectivity.isOnline) { s, online -> s.onboardingDone && s.lastRefreshAt == null && online }
                .distinctUntilChanged()
                .collect { needed -> if (needed) refresher.refresh() }
        }
    }

    fun selectLens(lens: LensFilter) {
        viewModelScope.launch { settings.setLens(tab, lens) }
    }

    fun refresh() = refresher.refresh()

    /** "See Global" from an empty lens keeps the lens (design state 10). */
    fun carryLensToGlobal(lens: LensFilter) {
        viewModelScope.launch { settings.setLens(Tab.GLOBAL, lens) }
    }

    fun dismiss(banner: Banner) {
        viewModelScope.launch {
            when (banner) {
                Banner.LimitedCoverage -> settings.current().region?.let { settings.dismissLimitedBanner(it) }
                is Banner.SourcesFailed -> settings.dismissFailedNote(clock.now())
                is Banner.Offline -> Unit // not dismissible
            }
        }
    }

    fun setSaved(row: StoryRow, saved: Boolean) {
        viewModelScope.launch { actions.setSaved(row.id, saved) }
    }

    fun markOpened(row: StoryRow) {
        viewModelScope.launch { actions.markOpened(row.id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
