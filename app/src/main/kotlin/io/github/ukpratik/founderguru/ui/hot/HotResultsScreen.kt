// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.hot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import io.github.ukpratik.founderguru.R
import io.github.ukpratik.founderguru.data.Tab
import io.github.ukpratik.founderguru.ui.components.FgButton
import io.github.ukpratik.founderguru.ui.components.FgIcon
import io.github.ukpratik.founderguru.ui.components.Hairline
import io.github.ukpratik.founderguru.ui.components.Kicker
import io.github.ukpratik.founderguru.ui.components.Labels
import io.github.ukpratik.founderguru.ui.components.Margin
import io.github.ukpratik.founderguru.ui.components.OfflineStrip
import io.github.ukpratik.founderguru.ui.components.StateTone
import io.github.ukpratik.founderguru.ui.components.StateView
import io.github.ukpratik.founderguru.ui.components.StatusBarSpacer
import io.github.ukpratik.founderguru.ui.components.StoryRow
import io.github.ukpratik.founderguru.ui.components.StoryRowActions
import io.github.ukpratik.founderguru.ui.components.Times
import io.github.ukpratik.founderguru.ui.feed.FeedHeader
import io.github.ukpratik.founderguru.ui.theme.Fg
import io.github.ukpratik.founderguru.ui.theme.FgType

/** What's hot results (SCR-05, US-006): a child of the tab; results come from the cache, so there is no loading state. */
@Composable
fun HotResultsScreen(state: HotUiState, now: Long, onBack: () -> Unit, rowActions: StoryRowActions) {
    val c = Fg.colors
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(c.bg)) {
        StatusBarSpacer()
        val (offline, since) = when (state) {
            is HotUiState.Content -> state.offline to state.offlineSince
            is HotUiState.NotQualifying -> state.offline to state.offlineSince
            HotUiState.Loading -> false to null
        }
        if (offline) {
            OfflineStrip(since?.let { stringResource(R.string.offline_strip, Times.clock(context, it)) } ?: stringResource(R.string.offline_strip_never))
        }
        val header = when (state) {
            is HotUiState.Content -> state.header
            is HotUiState.NotQualifying -> state.header
            HotUiState.Loading -> null
        } ?: return@Column
        val place = placeName(header)
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { FgIcon(R.drawable.ic_back, stringResource(R.string.back), c.ink2) }
            Text(stringResource(R.string.hot_results_bar, place), color = c.ink2, style = MaterialTheme.typography.titleSmall)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            when (state) {
                is HotUiState.Content -> {
                    item {
                        val spoken = pluralStringResource(R.plurals.hot_results_heading, state.count, state.sector.displayName, state.count)
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clearAndSetSemantics { contentDescription = spoken; heading() },
                        ) {
                            Column(Modifier.padding(start = Margin, end = Margin, top = 6.dp, bottom = 16.dp)) {
                                Kicker(stringResource(R.string.hot_rank, state.rank), c.accent)
                                Text(state.sector.displayName, Modifier.padding(top = 8.dp, bottom = 10.dp), color = c.ink, style = MaterialTheme.typography.displayMedium)
                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                    Text("${state.count}", color = c.ink, style = FgType.resultCount)
                                    Text(pluralStringResource(R.plurals.hot_results_count, state.count), color = c.meta, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Hairline(color = c.ink)
                        }
                    }
                    items(state.rows, key = { it.id }) { StoryRow(it, now, older = false, actions = rowActions) }
                }
                is HotUiState.NotQualifying -> {
                    item {
                        Column(Modifier.fillMaxWidth()) {
                            Text(
                                state.sector.displayName,
                                Modifier.padding(start = Margin, end = Margin, top = 4.dp, bottom = 14.dp),
                                color = c.ink,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            Hairline(color = c.ink)
                        }
                    }
                    item {
                        val name = state.sector.displayName
                        StateView(R.drawable.ic_info, stringResource(R.string.hot_gone_title, name), stringResource(R.string.hot_gone_body, name), StateTone.STATUS) {
                            FgButton(stringResource(R.string.hot_back_to, place), onBack, small = true)
                        }
                    }
                }
                HotUiState.Loading -> Unit
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun placeName(header: FeedHeader): String = when {
    header.tab == Tab.LOCAL && !header.globalOnly -> stringResource(Labels.region(header.region))
    header.globalOnly -> stringResource(R.string.feed_title_global)
    else -> stringResource(R.string.feed_title_world)
}
