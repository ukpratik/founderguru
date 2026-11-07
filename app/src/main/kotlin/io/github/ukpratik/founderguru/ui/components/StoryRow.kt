// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.ukpratik.founderguru.R
import io.github.ukpratik.founderguru.core.model.Lens
import io.github.ukpratik.founderguru.core.model.StoryRow
import io.github.ukpratik.founderguru.ui.theme.Fg

/** At most two lens labels per row (design notes "Lens distinction without colour"). */
fun lensesOf(row: StoryRow): List<Lens> = Lens.entries.filter { row.lenses and it.bit != 0 }.take(2)

class StoryRowActions(
    val open: (StoryRow) -> Unit,
    val setSaved: (StoryRow, Boolean) -> Unit,
    val share: (StoryRow) -> Unit,
    val openInBrowser: (StoryRow) -> Unit,
)

private val THUMB = 76.dp

/**
 * The story row (US-007, design-system §5): kicker, serif headline, fixed 76dp thumbnail box when the item has an image
 * URL (no reflow when it fails), snippet, **Source** · time, bookmark and overflow. One TalkBack stop with custom
 * actions; long-press opens the overflow menu. At font scale ≥ 1.5 the thumbnail moves below the snippet.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun StoryRow(row: StoryRow, now: Long, older: Boolean, actions: StoryRowActions, modifier: Modifier = Modifier) {
    val c = Fg.colors
    val res = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val large = isLargeFont()
    var menu by remember { mutableStateOf(false) }
    val lenses = lensesOf(row)
    val saved = row.savedAt != null
    val lensNames = lenses.map { stringResource(Labels.lens(it)) }
    val via = stringResource(R.string.via_global)
    val savedWord = stringResource(R.string.row_saved)
    val saveLabel = stringResource(if (saved) R.string.remove_from_saved else R.string.save)
    val shareLabel = stringResource(R.string.share)
    val browserLabel = stringResource(R.string.open_in_browser)
    val description = buildString {
        append(row.title.trimEnd('.')).append(". ")
        append(row.sourceName).append(", ").append(Times.spoken(res, row.publishedAt, now)).append('.')
        if (lensNames.isNotEmpty()) append(' ').append(lensNames.joinToString(", ")).append('.')
        if (row.viaGlobal) append(' ').append(via).append('.')
        if (saved) append(' ').append(savedWord).append('.')
    }
    val toggleSave = {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        actions.setSaved(row, !saved)
    }

    Column(
        modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { actions.open(row) }, onLongClick = { menu = true })
            .clearAndSetSemantics {
                testTag = "story-${row.id}"
                contentDescription = description
                onClick { actions.open(row); true }
                onLongClick { menu = true; true }
                customActions = listOf(
                    CustomAccessibilityAction(saveLabel) { toggleSave(); true },
                    CustomAccessibilityAction(shareLabel) { actions.share(row); true },
                    CustomAccessibilityAction(browserLabel) { actions.openInBrowser(row); true },
                )
            },
    ) {
        Column(Modifier.padding(start = Margin, end = Margin, top = 14.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (lenses.isNotEmpty() || row.viaGlobal) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    lensNames.forEachIndexed { i, name ->
                        if (i > 0) Text("·", color = c.meta, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal))
                        Kicker(name, c.accent)
                    }
                    if (row.viaGlobal) ViaGlobalTag(via)
                }
            }
            val headlineStyle = if (older) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
            val visited = row.openedAt != null
            val headline: @Composable () -> Unit = {
                Text(
                    row.title,
                    color = if (older || visited) c.ink2 else c.ink,
                    style = if (visited && !older) headlineStyle.copy(fontWeight = FontWeight.Medium) else headlineStyle,
                    maxLines = if (large) 5 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val snippet: @Composable () -> Unit = {
                if (row.snippet.isNotBlank()) {
                    Text(
                        row.snippet,
                        Modifier.padding(top = 2.dp),
                        color = c.ink2,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (large) 3 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val image = row.imageUrl
            if (large || image == null) {
                headline()
                snippet()
                if (image != null) Thumbnail(image, Modifier.padding(top = 6.dp))
            } else {
                Row {
                    Column(Modifier.weight(1f)) {
                        headline()
                        snippet()
                    }
                    Spacer(Modifier.width(14.dp))
                    Thumbnail(image, Modifier.padding(top = 3.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.ink2)) { append(row.sourceName) }
                        append(" · ")
                        append(Times.short(res, row.publishedAt, now))
                    },
                    Modifier.weight(1f),
                    color = c.meta,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(Modifier.offset(x = 12.dp)) {
                    IconButton(onClick = toggleSave, modifier = Modifier.size(48.dp).testTag("save-${row.id}")) {
                        FgIcon(
                            if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark,
                            saveLabel,
                            if (saved) c.accent else c.meta,
                            size = 20.dp,
                        )
                    }
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp).testTag("more-${row.id}")) {
                            FgIcon(R.drawable.ic_more, stringResource(R.string.more_options), c.meta, size = 20.dp)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = c.overlay) {
                            DropdownMenuItem(
                                text = { Text(shareLabel, color = c.ink, style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize)) },
                                leadingIcon = { FgIcon(R.drawable.ic_share, null, c.ink2, size = 20.dp) },
                                onClick = { menu = false; actions.share(row) },
                            )
                            DropdownMenuItem(
                                text = { Text(browserLabel, color = c.ink, style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize)) },
                                leadingIcon = { FgIcon(R.drawable.ic_open_in_browser, null, c.ink2, size = 20.dp) },
                                onClick = { menu = false; actions.openInBrowser(row) },
                            )
                        }
                    }
                }
            }
        }
        Hairline()
    }
}

@Composable
private fun ViaGlobalTag(label: String) {
    val c = Fg.colors
    Row(
        Modifier
            .border(1.dp, c.outline, MaterialTheme.shapes.extraSmall)
            .padding(start = 4.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FgIcon(R.drawable.ic_global, null, c.ink2, size = 12.dp)
        Spacer(Modifier.width(4.dp))
        Text(label.uppercase(), color = c.ink2, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
    }
}

/** Fixed 76dp `surface-2` box laid out before the image loads; on failure the empty box stays (D-017). */
@Composable
private fun Thumbnail(url: String, modifier: Modifier = Modifier) {
    val c = Fg.colors
    val px = with(LocalDensity.current) { THUMB.roundToPx() }
    val context = LocalContext.current
    val request = remember(url, px) { ImageRequest.Builder(context).data(url).size(px).build() }
    Box(
        modifier
            .size(THUMB)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(c.surface2)
            .testTag("thumb"),
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.size(THUMB),
            contentScale = ContentScale.Crop,
            colorFilter = if (c.isDark) DARK_THUMB else null,
        )
    }
}

/** Dark mode: `brightness(.82)` (design-system §2.2). */
private val DARK_THUMB = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.82f, 0.82f, 0.82f, 1f) })
