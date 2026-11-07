// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ukpratik.founderguru.R
import io.github.ukpratik.founderguru.ui.components.FeedAppBar
import io.github.ukpratik.founderguru.ui.components.FgButton
import io.github.ukpratik.founderguru.ui.components.Hairline
import io.github.ukpratik.founderguru.ui.components.Margin
import io.github.ukpratik.founderguru.ui.components.OfflineStrip
import io.github.ukpratik.founderguru.ui.components.StateTone
import io.github.ukpratik.founderguru.ui.components.StateView
import io.github.ukpratik.founderguru.ui.components.StatusBarSpacer
import io.github.ukpratik.founderguru.ui.components.StoryRow
import io.github.ukpratik.founderguru.ui.components.StoryRowActions
import io.github.ukpratik.founderguru.ui.theme.Fg
import io.github.ukpratik.founderguru.ui.theme.FgType

/** Saved (SCR-06): newest saved first, never pruned; remove = tap the filled bookmark (Undo in the snackbar). */
@Composable
fun SavedScreen(state: SavedUiState, now: Long, homeName: String, onGoHome: () -> Unit, onSearch: () -> Unit, rowActions: StoryRowActions) {
    val c = Fg.colors
    Column(Modifier.fillMaxSize().background(c.bg)) {
        StatusBarSpacer()
        val offline = (state as? SavedUiState.Content)?.offline ?: (state as? SavedUiState.Empty)?.offline ?: false
        if (offline) OfflineStrip(stringResource(R.string.saved_offline))
        LazyColumn(Modifier.fillMaxSize().testTag("saved")) {
            item(key = "appbar") { FeedAppBar(stringResource(R.string.saved_overline), stringResource(R.string.saved_title), onSearch) }
            item(key = "sub") {
                val count = (state as? SavedUiState.Content)?.rows?.size ?: 0
                Column {
                    Text(stringResource(R.string.saved_sub), Modifier.padding(horizontal = Margin), color = c.ink2, style = FgType.dateline)
                    Text(
                        pluralStringResource(R.plurals.story_count, count, count),
                        Modifier.padding(start = Margin, end = Margin, top = 4.dp, bottom = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        color = c.meta,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Hairline(Modifier.padding(horizontal = Margin), color = c.ink)
                }
            }
            when (state) {
                SavedUiState.Loading -> Unit
                is SavedUiState.Empty -> item {
                    StateView(R.drawable.ic_bookmark, stringResource(R.string.saved_empty_title), stringResource(R.string.saved_empty_body), StateTone.STATUS) {
                        FgButton(stringResource(R.string.saved_go_to, homeName), onGoHome, small = true)
                    }
                }
                is SavedUiState.Content -> items(state.rows, key = { it.id }) { StoryRow(it, now, older = false, actions = rowActions) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
