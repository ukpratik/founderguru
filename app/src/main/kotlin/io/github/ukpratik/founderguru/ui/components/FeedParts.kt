// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ukpratik.founderguru.R
import io.github.ukpratik.founderguru.core.feed.LensFilter
import io.github.ukpratik.founderguru.core.feed.SectorCount
import io.github.ukpratik.founderguru.ui.feed.DatelineStatus
import io.github.ukpratik.founderguru.ui.theme.Fg
import io.github.ukpratik.founderguru.ui.theme.FgType

/** Tab title block: accent overline + serif title, and the search action (`.appbar`). */
@Composable
fun FeedAppBar(overline: String, title: String, onSearch: (() -> Unit)?) {
    val c = Fg.colors
    Row(Modifier.fillMaxWidth().padding(start = Margin, end = 8.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Kicker(overline, c.accent)
            Text(
                title,
                Modifier.padding(top = 6.dp).semantics { heading() },
                color = c.ink,
                style = MaterialTheme.typography.headlineLarge,
            )
        }
        if (onSearch != null) {
            IconButton(onClick = onSearch, modifier = Modifier.size(48.dp)) {
                FgIcon(R.drawable.ic_search, stringResource(R.string.search_stories), c.ink2)
            }
        }
    }
}

/** Date (serif italic) and refresh status; a polite live region so refresh results are announced (design-system §5). */
@Composable
fun Dateline(now: Long, status: DatelineStatus) {
    val c = Fg.colors
    val context = LocalContext.current
    val right = when (status) {
        is DatelineStatus.Getting -> pluralStringResource(R.plurals.dateline_getting, status.sources, status.sources)
        DatelineStatus.Refreshing -> stringResource(R.string.dateline_refreshing)
        is DatelineStatus.Updated -> {
            val at = status.at
            val minutes = if (at == null) null else ((now - at) / 60_000).coerceAtLeast(0)
            when {
                at == null || minutes == null -> stringResource(R.string.dateline_never)
                status.offline || minutes >= 60 -> stringResource(R.string.dateline_updated_at, Times.clock(context, at))
                minutes < 1 -> stringResource(R.string.dateline_updated_now)
                else -> stringResource(R.string.dateline_updated_minutes, minutes.toInt())
            }
        }
    }
    val large = isLargeFont()
    Column {
        val date: @Composable () -> Unit = { Text(Times.dateline(now), color = c.ink2, style = FgType.dateline) }
        val stat: @Composable (Modifier) -> Unit = { m ->
            Text(right, m.semantics { liveRegion = LiveRegionMode.Polite }, color = c.meta, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
        }
        if (large) {
            Column(Modifier.padding(start = Margin, end = Margin, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                date()
                stat(Modifier)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = Margin, end = Margin, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f)) { date() }
                Spacer(Modifier.width(12.dp))
                stat(Modifier)
            }
        }
        Hairline(color = c.ink)
    }
}

/** "What's hot this week" (US-006): rank · sector · count, hairline-separated, 48dp items. Hidden when empty. */
@Composable
fun HotStrip(hot: List<SectorCount>, onOpen: (SectorCount) -> Unit) {
    val c = Fg.colors
    Column(Modifier.padding(top = 14.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = Margin, end = Margin, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.hot_title).uppercase(),
                Modifier.weight(1f).semantics { heading() },
                color = c.ink,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(stringResource(R.string.hot_subtitle), color = c.meta, style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize))
        }
        LazyRow(contentPadding = PaddingValues(horizontal = Margin)) {
            itemsIndexed(hot, key = { _, s -> s.sector.name }) { i, s ->
                val description = pluralStringResource(R.plurals.hot_item_description, s.count, s.sector.displayName, s.count)
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clickable { onOpen(s) }
                        .clearAndSetSemantics { contentDescription = description; role = Role.Button }
                        .testTag("hot-${s.sector.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", color = c.meta, style = FgType.hotRank)
                    Spacer(Modifier.width(8.dp))
                    Text(s.sector.displayName, color = c.ink, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(8.dp))
                    Text("${s.count}", color = c.accent, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
                    if (i < hot.lastIndex) {
                        Spacer(Modifier.width(16.dp))
                        Box(Modifier.width(1.dp).height(24.dp).background(c.rule))
                        Spacer(Modifier.width(16.dp))
                    }
                }
            }
        }
    }
}

/** Single-select lens chips in a `LazyRow` (never wraps, also at 200%). Disabled during the first sync. */
@Composable
fun LensChips(selected: LensFilter, enabled: Boolean, onSelect: (LensFilter) -> Unit) {
    val c = Fg.colors
    val group = stringResource(R.string.lens_group)
    Column {
        LazyRow(
            Modifier.fillMaxWidth().semantics { selectableGroup(); contentDescription = group },
            contentPadding = PaddingValues(start = Margin, end = Margin, top = 10.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(Labels.CHIPS.size) { i ->
                val lens = Labels.CHIPS[i]
                val label = stringResource(Labels.lens(lens))
                val isOn = lens == selected
                val chipDescription = stringResource(R.string.lens_chip_description, label)
                FilterChip(
                    selected = isOn,
                    onClick = { onSelect(lens) },
                    enabled = enabled,
                    label = { Text(label, style = FgType.chip) },
                    leadingIcon = if (isOn) {
                        { FgIcon(R.drawable.ic_check, null, c.bg, size = 16.dp) }
                    } else {
                        null
                    },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = c.ink2,
                        selectedContainerColor = c.ink,
                        selectedLabelColor = c.bg,
                        selectedLeadingIconColor = c.bg,
                        disabledContainerColor = Color.Transparent,
                        disabledLabelColor = c.ink2.copy(alpha = 0.5f),
                        disabledSelectedContainerColor = c.ink.copy(alpha = 0.5f),
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = enabled,
                        selected = isOn,
                        borderColor = c.outline,
                        selectedBorderColor = c.ink,
                        disabledBorderColor = c.outline.copy(alpha = 0.5f),
                        disabledSelectedBorderColor = c.ink.copy(alpha = 0.5f),
                    ),
                    // 34dp visual; M3 extends the touch target to 48dp (minimumInteractiveComponentSize).
                    modifier = Modifier
                        .height(34.dp)
                        .semantics { contentDescription = chipDescription }
                        .testTag("lens-${lens}"),
                )
            }
        }
        Hairline()
    }
}

/** "NEW SINCE YOUR LAST VISIT · 6" (accent) or "EARLIER" (muted). A heading; not sticky. */
@Composable
fun SectionDivider(text: String, muted: Boolean) {
    val c = Fg.colors
    Row(
        Modifier.fillMaxWidth().padding(start = Margin, end = Margin, top = 18.dp, bottom = 4.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Kicker(text, if (muted) c.meta else c.accent)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(if (muted) c.rule else c.accent.copy(alpha = 0.6f)))
    }
}

/** The caught-up marker: check circle, serif italic title, last-visit line, ink rules above and below. A heading. */
@Composable
fun CaughtUpBlock(title: String, body: String, top: Boolean) {
    val c = Fg.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = Margin, end = Margin, top = if (top) 16.dp else 8.dp, bottom = 4.dp)
            .semantics(mergeDescendants = true) { heading() }
            .testTag("caught-up"),
    ) {
        Hairline(color = c.ink)
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(36.dp).background(c.accentSoft, CircleShape), contentAlignment = Alignment.Center) {
                FgIcon(R.drawable.ic_check, null, c.accent, size = 20.dp)
            }
            Spacer(Modifier.height(8.dp))
            Text(title, color = c.ink, style = MaterialTheme.typography.headlineSmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), textAlign = TextAlign.Center)
            Text(body, Modifier.padding(top = 4.dp), color = c.meta, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
        Hairline(color = c.ink)
    }
}

/** First-sync skeleton rows: thumbnail block on 2 of 3 rows, pulse 1.4 s (off with "Remove animations"). */
@Composable
fun SkeletonRows(count: Int = 5) {
    val c = Fg.colors
    val still = animationsOff()
    val alpha = if (still) {
        1f
    } else {
        val t = rememberInfiniteTransition(label = "skeleton")
        t.animateFloat(1f, 0.5f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse").value
    }
    val label = stringResource(R.string.loading_stories)
    Column(Modifier.semantics { contentDescription = label; liveRegion = LiveRegionMode.Polite }.testTag("skeleton")) {
        repeat(count) { i ->
            Row(Modifier.fillMaxWidth().padding(start = Margin, end = Margin, top = 16.dp, bottom = 18.dp).alpha(alpha)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Bar(0.3f, 10)
                    Bar(0.96f, 16)
                    Bar(0.8f, 16)
                    Bar(0.9f, 12)
                    Bar(0.4f, 10)
                }
                if (i % 3 != 1) {
                    Spacer(Modifier.width(14.dp))
                    Box(Modifier.size(76.dp).background(c.surface2, MaterialTheme.shapes.extraSmall))
                } else {
                    Spacer(Modifier.width(90.dp).fillMaxHeight())
                }
            }
            Hairline()
        }
    }
}

@Composable
private fun Bar(fraction: Float, height: Int) =
    Box(Modifier.fillMaxWidth(fraction).height(height.dp).background(Fg.colors.surface2, MaterialTheme.shapes.extraSmall))
