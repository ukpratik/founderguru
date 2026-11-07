// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ukpratik.founderguru.R
import io.github.ukpratik.founderguru.ui.components.FgIcon
import io.github.ukpratik.founderguru.ui.components.FgTextButton
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

/** Search (SCR-10): full-screen route, keyboard open on entry, matches marked with weight and tint. */
@Composable
fun SearchScreen(
    query: String,
    state: SearchUiState,
    offline: Boolean,
    now: Long,
    onQuery: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    rowActions: StoryRowActions,
) {
    val c = Fg.colors
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { if (query.isEmpty()) focus.requestFocus() }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        StatusBarSpacer()
        if (offline) OfflineStrip(stringResource(R.string.search_offline))
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { FgIcon(R.drawable.ic_back, stringResource(R.string.back), c.ink2) }
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp).background(c.surface2, CircleShape).padding(start = 18.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val hint = stringResource(R.string.search_hint)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(hint, color = c.meta, style = MaterialTheme.typography.bodyLarge)
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("search-field").semantics { contentDescription = hint },
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onClear(); focus.requestFocus() }, modifier = Modifier.size(48.dp)) {
                        FgIcon(R.drawable.ic_close, stringResource(R.string.search_clear), c.ink2, size = 20.dp)
                    }
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize().testTag("search-results")) {
            when (state) {
                SearchUiState.Idle -> {
                    item {
                        Text(stringResource(R.string.search_scope), Modifier.padding(start = Margin, end = Margin, top = 10.dp), color = c.ink2, style = MaterialTheme.typography.bodySmall)
                    }
                    item { StateView(R.drawable.ic_search, stringResource(R.string.search_idle_title), stringResource(R.string.search_idle_body), StateTone.STATUS) }
                }
                is SearchUiState.Results -> {
                    item { ResultHeader(state.q, state.rows.size) }
                    items(state.rows, key = { it.id }) { StoryRow(it, now, older = false, actions = rowActions, highlight = state.q) }
                }
                is SearchUiState.NoResults -> {
                    item { ResultHeader(state.q, 0) }
                    item {
                        StateView(R.drawable.ic_search, stringResource(R.string.search_none_title, shortQuery(state.q)), stringResource(R.string.search_none_body), StateTone.STATUS) {
                            FgTextButton(stringResource(R.string.search_clear), { onClear(); focus.requestFocus() })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ResultHeader(q: String, count: Int) {
    val c = Fg.colors
    Column(Modifier.fillMaxWidth()) {
        // BUG-008: the echoed query is capped, so a long paste can't fill the screen.
        Text(
            q,
            Modifier.padding(start = Margin, end = Margin, top = 10.dp).testTag("search-query"),
            color = c.ink,
            style = MaterialTheme.typography.headlineMedium,
            maxLines = QUERY_TITLE_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            if (count == 0) pluralStringResource(R.plurals.story_count, 0, 0) else pluralStringResource(R.plurals.search_count, count, count),
            Modifier.padding(start = Margin, end = Margin, top = 4.dp, bottom = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
            color = c.meta,
            style = FgType.dateline,
        )
        Hairline(Modifier.padding(horizontal = Margin), color = c.ink)
    }
}

/** The query as echoed in the no-results title (BUG-008): at most [QUERY_TITLE_CHARS] characters. */
internal fun shortQuery(q: String): String = if (q.length <= QUERY_TITLE_CHARS) q else q.take(QUERY_TITLE_CHARS).trimEnd() + "…"

private const val QUERY_TITLE_LINES = 2
private const val QUERY_TITLE_CHARS = 60
