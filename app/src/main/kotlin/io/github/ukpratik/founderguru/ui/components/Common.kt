// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import android.provider.Settings
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ukpratik.founderguru.R
import io.github.ukpratik.founderguru.ui.theme.Fg
import io.github.ukpratik.founderguru.ui.theme.FgType

/** Content margin for feed rows and headers (design-system §4: 20dp, the editorial measure). */
val Margin = 20.dp

/** At font scale ≥ 1.5 the layouts switch (design-system §3 "Scaling"). */
@Composable
fun isLargeFont(): Boolean = LocalDensity.current.fontScale >= 1.5f

/** True when the user turned animations off ("Remove animations"); the skeleton pulse stops. */
@Composable
fun animationsOff(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

@Composable
fun FgIcon(@DrawableRes id: Int, contentDescription: String?, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) =
    Icon(painterResource(id), contentDescription, modifier.size(size), tint = tint)

/** Pinned `bg` spacer under the transparent status bar (design-system §6 "System bars"). */
@Composable
fun StatusBarSpacer() = Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Fg.colors.bg))

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Fg.colors.rule) =
    Box(modifier.fillMaxWidth().height(1.dp).background(color))

/** Uppercase tracked label (`labelSmall`); TalkBack reads [text] in sentence case (design-system §3 "Small-caps"). */
@Composable
fun Kicker(text: String, color: Color, modifier: Modifier = Modifier) =
    // M5 TalkBack pass: read the words in their normal case, so short all-caps labels ("INDIA", "AI") aren't spelled out.
    Text(text.uppercase(), modifier.semantics { contentDescription = text }, color = color, style = MaterialTheme.typography.labelSmall)

/** Filled CTA: ink fill, bg text, full shape, 52dp (48dp for `small`). */
@Composable
fun FgButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, small: Boolean = false) {
    val c = Fg.colors
    Button(
        onClick = onClick,
        modifier = modifier.height(if (small) 48.dp else 52.dp).widthIn(min = 160.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c.ink, contentColor = c.bg),
        contentPadding = PaddingValues(horizontal = 28.dp),
    ) {
        val label = MaterialTheme.typography.labelLarge
        Text(text, style = if (small) label else label.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize))
    }
}

@Composable
fun FgOutlinedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Fg.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp).widthIn(min = 160.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.ink),
        border = BorderStroke(1.dp, c.outline),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Accent text button, 48dp tall (`.tbtn`). */
@Composable
fun FgTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier.defaultMinSize(minHeight = 48.dp)) {
        Text(text, color = Fg.colors.accent, style = MaterialTheme.typography.labelLarge.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize))
    }
}

/** A tag such as "Suggested" (accent-soft fill) or "Limited coverage" (outlined); text carries the meaning. */
@Composable
fun Tag(text: String, filled: Boolean) {
    val c = Fg.colors
    val shape = MaterialTheme.shapes.extraSmall
    val base = if (filled) Modifier.background(c.accentSoft, shape) else Modifier.border(1.dp, c.outline, shape)
    Text(
        text.uppercase(),
        base.padding(horizontal = 6.dp, vertical = 4.dp),
        color = if (filled) c.accent else c.ink2,
        style = FgType.tag,
    )
}

enum class StateTone { STATUS, ALERT }

/** Centred empty / error state (design-system §5): icon circle, serif title, body, stacked actions. */
@Composable
fun StateView(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    tone: StateTone,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val c = Fg.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, top = 48.dp, bottom = 32.dp)
            .semantics { liveRegion = if (tone == StateTone.ALERT) LiveRegionMode.Assertive else LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).background(c.surface2, CircleShape), contentAlignment = Alignment.Center) {
            FgIcon(icon, null, c.ink2)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            Modifier.semantics { heading() },
            color = c.ink,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            Modifier.padding(top = 8.dp, bottom = 22.dp).widthIn(max = 290.dp),
            color = c.ink2,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

/** Inset banner (design-system §5): icon, bold lead + sentence, optional action, dismiss. Polite live region. */
@Composable
fun FgBanner(
    @DrawableRes icon: Int,
    warning: Boolean,
    lead: String,
    body: String,
    action: String?,
    onAction: () -> Unit,
    onDismiss: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val c = Fg.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp)
            .background(c.surface2, MaterialTheme.shapes.small)
            .padding(start = 14.dp, top = 12.dp, end = 4.dp, bottom = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.Top,
    ) {
        FgIcon(icon, null, if (warning) c.accent else c.ink2, Modifier.padding(top = 1.dp), size = 20.dp)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f).padding(bottom = 8.dp)) {
            Text(
                buildAnnotatedString {
                    if (lead.isNotEmpty()) {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(lead) }
                        append(' ')
                    }
                    append(body)
                },
                color = c.ink,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (action != null) FgTextButton(action, onAction, Modifier.padding(top = 4.dp))
        }
        if (onDismiss != null) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                FgIcon(R.drawable.ic_close, stringResource(R.string.dismiss), c.ink2, size = 20.dp)
            }
        } else {
            Spacer(Modifier.size(12.dp))
        }
    }
}

/** Inverse strip pinned under the status bar while offline (not dismissible, polite live region). */
@Composable
fun OfflineStrip(text: String) {
    val c = Fg.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.inv)
            .padding(horizontal = Margin, vertical = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FgIcon(R.drawable.ic_cloud_off, null, c.invOn, size = 18.dp)
        Spacer(Modifier.size(10.dp))
        Text(text, color = c.invOn, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
    }
}
