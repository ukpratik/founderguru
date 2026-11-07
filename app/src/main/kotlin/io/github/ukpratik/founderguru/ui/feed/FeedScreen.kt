// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.ukpratik.founderguru.R
import io.github.ukpratik.founderguru.core.feed.Banner
import io.github.ukpratik.founderguru.core.feed.CaughtUpMarker
import io.github.ukpratik.founderguru.core.feed.FeedSections
import io.github.ukpratik.founderguru.core.feed.LensFilter
import io.github.ukpratik.founderguru.core.feed.SectorCount
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.data.Tab
import io.github.ukpratik.founderguru.ui.components.CaughtUpBlock
import io.github.ukpratik.founderguru.ui.components.Dateline
import io.github.ukpratik.founderguru.ui.components.FeedAppBar
import io.github.ukpratik.founderguru.ui.components.FgBanner
import io.github.ukpratik.founderguru.ui.components.FgButton
import io.github.ukpratik.founderguru.ui.components.FgOutlinedButton
import io.github.ukpratik.founderguru.ui.components.FgTextButton
import io.github.ukpratik.founderguru.ui.components.Hairline
import io.github.ukpratik.founderguru.ui.components.HotStrip
import io.github.ukpratik.founderguru.ui.components.Labels
import io.github.ukpratik.founderguru.ui.components.LensChips
import io.github.ukpratik.founderguru.ui.components.Margin
import io.github.ukpratik.founderguru.ui.components.OfflineStrip
import io.github.ukpratik.founderguru.ui.components.SectionDivider
import io.github.ukpratik.founderguru.ui.components.SkeletonRows
import io.github.ukpratik.founderguru.ui.components.StateTone
import io.github.ukpratik.founderguru.ui.components.StateView
import io.github.ukpratik.founderguru.ui.components.StatusBarSpacer
import io.github.ukpratik.founderguru.ui.components.StoryRow
import io.github.ukpratik.founderguru.ui.components.StoryRowActions
import io.github.ukpratik.founderguru.ui.components.Times
import io.github.ukpratik.founderguru.ui.theme.Fg

/** Everything a feed screen can ask for; navigation targets are resolved by the caller. */
class FeedCallbacks(
    val selectLens: (LensFilter) -> Unit,
    val refresh: () -> Unit,
    val dismiss: (Banner) -> Unit,
    val openHot: (SectorCount) -> Unit,
    /** "See Global", carrying the lens (design state 10). */
    val seeGlobal: (LensFilter) -> Unit,
    val openSources: () -> Unit,
    val openSearch: () -> Unit,
    val row: StoryRowActions,
)

/** Key of the "New since" divider, the re-tap scroll target (design notes "Re-tap the Local nav item"). */
const val NEW_DIVIDER_KEY = "divider-new"

/** Local and Global (SCR-03/04): every US-011 state, What's hot, lens chips, sections and banners. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(state: FeedUiState, now: Long, callbacks: FeedCallbacks, listState: LazyListState = rememberLazyListState()) {
    val c = Fg.colors
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(c.bg)) {
        StatusBarSpacer()
        val banners = when (state) {
            is FeedUiState.Content -> state.banners
            is FeedUiState.Empty -> state.banners
            else -> emptyList()
        }
        banners.filterIsInstance<Banner.Offline>().firstOrNull()?.let { offline ->
            OfflineStrip(
                offline.since?.let { stringResource(R.string.offline_strip, Times.clock(context, it)) } ?: stringResource(R.string.offline_strip_never),
            )
        }
        val refreshable = state is FeedUiState.Content || state is FeedUiState.Empty
        val refreshing = (state as? FeedUiState.Content)?.isRefreshing ?: (state as? FeedUiState.Empty)?.isRefreshing ?: false
        val list: @Composable () -> Unit = {
            LazyColumn(Modifier.fillMaxSize().testTag("feed"), state = listState) {
                when (state) {
                    FeedUiState.Loading -> Unit
                    is FeedUiState.FirstSync -> {
                        header(state.header, callbacks)
                        item { Dateline(now, state.dateline) }
                        item { LensChips(state.lens, enabled = false, onSelect = {}) }
                        item { SkeletonRows() }
                    }
                    is FeedUiState.FirstSyncOffline -> {
                        header(state.header, callbacks)
                        item { Hairline(Modifier.padding(horizontal = Margin), color = c.ink) }
                        item { OfflineState(callbacks) }
                    }
                    is FeedUiState.AllFailed -> {
                        header(state.header, callbacks)
                        item { Hairline(Modifier.padding(horizontal = Margin), color = c.ink) }
                        item { AllFailedState(state, callbacks) }
                    }
                    is FeedUiState.Content -> {
                        header(state.header, callbacks)
                        item { Dateline(now, state.dateline) }
                        banners(state.banners, callbacks)
                        if (state.hot.isNotEmpty()) item { HotStrip(state.hot, callbacks.openHot) }
                        item { LensChips(state.lens, enabled = true, onSelect = callbacks.selectLens) }
                        sections(state.sections, now, context, callbacks)
                    }
                    is FeedUiState.Empty -> {
                        header(state.header, callbacks)
                        item { Dateline(now, state.dateline) }
                        banners(state.banners, callbacks)
                        if (state.hot.isNotEmpty()) item { HotStrip(state.hot, callbacks.openHot) }
                        if (state.reason != EmptyReason.ALL_SOURCES_OFF) {
                            item { LensChips(state.lens, enabled = true, onSelect = callbacks.selectLens) }
                        }
                        item { EmptyState(state, callbacks) }
                    }
                }
            }
        }
        if (refreshable) {
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = callbacks.refresh, modifier = Modifier.fillMaxSize()) { list() }
        } else {
            list()
        }
    }
}

private fun LazyListScope.header(header: FeedHeader, callbacks: FeedCallbacks) = item(key = "appbar") {
    val overline = when {
        header.globalOnly -> stringResource(R.string.app_name)
        header.tab == Tab.LOCAL -> stringResource(R.string.feed_overline_local)
        else -> stringResource(R.string.feed_overline_global)
    }
    val title = when {
        header.globalOnly -> stringResource(R.string.feed_title_global)
        header.tab == Tab.LOCAL -> stringResource(Labels.region(header.region))
        else -> stringResource(R.string.feed_title_world)
    }
    FeedAppBar(overline, title, callbacks.openSearch)
}

/** At most two inset banners; the offline strip is pinned outside the list. */
private fun LazyListScope.banners(banners: List<Banner>, callbacks: FeedCallbacks) {
    banners.filter { it !is Banner.Offline }.take(2).forEach { banner ->
        item(key = "banner-${banner::class.simpleName}") {
            when (banner) {
                Banner.LimitedCoverage -> FgBanner(
                    icon = R.drawable.ic_info,
                    warning = false,
                    lead = stringResource(R.string.banner_limited_lead),
                    body = stringResource(R.string.banner_limited_body),
                    action = stringResource(R.string.see_global),
                    onAction = { callbacks.seeGlobal(LensFilter.All) },
                    onDismiss = { callbacks.dismiss(banner) },
                )
                is Banner.SourcesFailed -> FgBanner(
                    icon = R.drawable.ic_warning,
                    warning = true,
                    lead = pluralStringResource(R.plurals.banner_failed_lead, banner.count, banner.count),
                    body = stringResource(R.string.banner_failed_body),
                    action = stringResource(R.string.view_sources),
                    onAction = callbacks.openSources,
                    onDismiss = { callbacks.dismiss(banner) },
                )
                is Banner.Offline -> Unit
            }
        }
    }
}

private fun LazyListScope.sections(s: FeedSections, now: Long, context: android.content.Context, callbacks: FeedCallbacks) {
    val lensName: @Composable () -> String? = { (s.lens as? LensFilter.Only)?.let { stringResource(Labels.lens(it.lens)) } }
    when (s.marker) {
        CaughtUpMarker.END_OF_LIST -> {
            items(s.earlier, key = { it.id }) { StoryRow(it, now, older = false, actions = callbacks.row) }
            item(key = "caught-up") {
                val lens = lensName()
                CaughtUpBlock(
                    title = lens?.let { stringResource(R.string.caught_up_lens, it) } ?: stringResource(R.string.caught_up),
                    body = stringResource(R.string.caught_up_first_sync),
                    top = false,
                )
            }
        }
        CaughtUpMarker.AFTER_NEW -> {
            item(key = NEW_DIVIDER_KEY) {
                val lens = lensName()
                SectionDivider(
                    lens?.let { stringResource(R.string.marker_new_lens, it, s.newCount) } ?: stringResource(R.string.marker_new, s.newCount),
                    muted = false,
                )
            }
            items(s.new, key = { it.id }) { StoryRow(it, now, older = false, actions = callbacks.row) }
            item(key = "caught-up") {
                val lens = lensName()
                val since = s.anchor?.let { Times.visit(context, it, now) }.orEmpty()
                CaughtUpBlock(
                    title = lens?.let { stringResource(R.string.caught_up_lens, it) } ?: stringResource(R.string.caught_up),
                    body = if (lens != null) stringResource(R.string.caught_up_since_lens, since) else stringResource(R.string.caught_up_since, since),
                    top = false,
                )
            }
            earlier(s, now, callbacks)
        }
        CaughtUpMarker.TOP_NOTHING_NEW -> {
            item(key = "caught-up") {
                val lens = lensName()
                val since = s.anchor?.let { Times.visit(context, it, now) }.orEmpty()
                CaughtUpBlock(
                    title = lens?.let { stringResource(R.string.nothing_new_lens, it, since) } ?: stringResource(R.string.nothing_new, since),
                    body = stringResource(R.string.nothing_new_body),
                    top = true,
                )
            }
            earlier(s, now, callbacks)
        }
    }
}

private fun LazyListScope.earlier(s: FeedSections, now: Long, callbacks: FeedCallbacks) {
    if (s.earlier.isEmpty()) return
    item(key = "divider-earlier") { SectionDivider(stringResource(R.string.marker_earlier), muted = true) }
    items(s.earlier, key = { it.id }) { StoryRow(it, now, older = true, actions = callbacks.row) }
}

@Composable
private fun OfflineState(callbacks: FeedCallbacks) = StateView(
    icon = R.drawable.ic_cloud_off,
    title = stringResource(R.string.offline_title),
    body = stringResource(R.string.offline_body),
    tone = StateTone.ALERT,
) { FgButton(stringResource(R.string.retry), callbacks.refresh, small = true) }

@Composable
private fun AllFailedState(state: FeedUiState.AllFailed, callbacks: FeedCallbacks) {
    val n = state.sourceCount
    if (state.canSeeGlobal) {
        val region = stringResource(Labels.region(state.header.region))
        StateView(
            icon = R.drawable.ic_warning,
            title = stringResource(R.string.all_failed_title, region),
            body = pluralStringResource(R.plurals.all_failed_body, n, n, region),
            tone = StateTone.ALERT,
        ) {
            FgButton(stringResource(R.string.retry), callbacks.refresh, small = true)
            FgOutlinedButton(stringResource(R.string.see_global), { callbacks.seeGlobal(LensFilter.All) })
            FgTextButton(stringResource(R.string.source_status), callbacks.openSources)
        }
    } else {
        StateView(
            icon = R.drawable.ic_warning,
            title = stringResource(R.string.all_failed_title_global),
            body = pluralStringResource(R.plurals.all_failed_body_global, n, n),
            tone = StateTone.ALERT,
        ) {
            FgButton(stringResource(R.string.retry), callbacks.refresh, small = true)
            FgTextButton(stringResource(R.string.source_status), callbacks.openSources)
        }
    }
}

@Composable
private fun EmptyState(state: FeedUiState.Empty, callbacks: FeedCallbacks) {
    val onGlobal = state.header.tabRegion == Region.GLOBAL
    val region = stringResource(Labels.region(state.header.region))
    when (state.reason) {
        EmptyReason.LENS_EMPTY -> {
            val lens = (state.lens as? LensFilter.Only)?.lens
            val noun = lens?.let { stringResource(Labels.lensNoun(it)) }.orEmpty()
            if (onGlobal) {
                StateView(R.drawable.ic_search, stringResource(R.string.empty_lens_title_global, noun), stringResource(R.string.empty_lens_body_global), StateTone.STATUS) {
                    FgTextButton(stringResource(R.string.show_all_stories), { callbacks.selectLens(LensFilter.All) })
                }
            } else {
                StateView(R.drawable.ic_search, stringResource(R.string.empty_lens_title, noun, region), stringResource(R.string.empty_lens_body), StateTone.STATUS) {
                    FgButton(stringResource(R.string.see_global), { callbacks.seeGlobal(state.lens) }, small = true)
                    FgTextButton(stringResource(R.string.show_all_stories), { callbacks.selectLens(LensFilter.All) })
                }
            }
        }
        EmptyReason.NO_STORIES_IN_REGION -> if (onGlobal) {
            StateView(R.drawable.ic_rss, stringResource(R.string.empty_region_title_global), stringResource(R.string.empty_region_body_global), StateTone.STATUS)
        } else {
            StateView(
                R.drawable.ic_rss,
                stringResource(R.string.empty_region_title, region),
                pluralStringResource(R.plurals.empty_region_body, state.sourceCount, state.sourceCount),
                StateTone.STATUS,
            ) { FgButton(stringResource(R.string.see_global), { callbacks.seeGlobal(LensFilter.All) }, small = true) }
        }
        EmptyReason.ALL_SOURCES_OFF -> if (onGlobal) {
            StateView(R.drawable.ic_rss, stringResource(R.string.sources_off_title_global), stringResource(R.string.sources_off_body_global), StateTone.STATUS) {
                FgButton(stringResource(R.string.turn_sources_on), callbacks.openSources, small = true)
            }
        } else {
            StateView(
                R.drawable.ic_rss,
                stringResource(R.string.sources_off_title, region),
                stringResource(R.string.sources_off_body, region),
                StateTone.STATUS,
            ) {
                FgButton(stringResource(R.string.turn_sources_on), callbacks.openSources, small = true)
                FgOutlinedButton(stringResource(R.string.see_global), { callbacks.seeGlobal(LensFilter.All) })
            }
        }
    }
}
