// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ukpratik.founderguru.R
import io.github.ukpratik.founderguru.ui.theme.Fg

/** Back bar of a pushed screen (Settings sub-screens): back arrow + the parent's name (`titleSmall`, `ink-2`). */
@Composable
fun BackBar(label: String, onBack: () -> Unit) {
    val c = Fg.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 4.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { FgIcon(R.drawable.ic_back, stringResource(R.string.back), c.ink2) }
        Text(label, color = c.ink2, style = MaterialTheme.typography.titleSmall)
    }
}

/** Serif sub-screen title (`headlineMedium`) with an optional intro line and the ink rule under it. */
@Composable
fun ScreenTitle(title: String, intro: String? = null) {
    val c = Fg.colors
    Column(Modifier.fillMaxWidth()) {
        Text(title, Modifier.padding(start = Margin, end = Margin, top = 4.dp).semantics { heading() }, color = c.ink, style = MaterialTheme.typography.headlineMedium)
        if (intro != null) {
            Text(intro, Modifier.padding(start = Margin, end = Margin, top = 8.dp), color = c.ink2, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.size(14.dp))
        Hairline(Modifier.padding(horizontal = Margin), color = c.ink)
    }
}

/** Settings section header: `labelSmall` uppercase accent, heading semantics. */
@Composable
fun SectionHeader(text: String, color: Color = Fg.colors.accent) {
    Kicker(text, color, Modifier.padding(start = Margin, end = Margin, top = 22.dp, bottom = 6.dp).semantics { heading() })
}

/**
 * Settings row (design-system §5): leading icon 24dp, headline `bodyLarge` 500, supporting `bodySmall`, trailing
 * chevron / external-link icon. The whole row is the target (≥ 64dp).
 */
@Composable
fun SettingsRow(
    @DrawableRes icon: Int?,
    title: String,
    supporting: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: Trailing = Trailing.CHEVRON,
    warning: Boolean = false,
    below: (@Composable () -> Unit)? = null,
) {
    val c = Fg.colors
    Row(
        modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .heightIn(min = 64.dp)
            .padding(start = Margin, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            FgIcon(icon, null, c.ink2)
            Spacer(Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            if (supporting != null) {
                Text(
                    supporting,
                    Modifier.padding(top = 2.dp),
                    color = if (warning) c.accent else c.meta,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            below?.invoke()
        }
        when (trailing) {
            Trailing.CHEVRON -> FgIcon(R.drawable.ic_chevron_right, null, c.meta, Modifier.padding(start = 8.dp), size = 20.dp)
            Trailing.EXTERNAL -> FgIcon(R.drawable.ic_open_in_new, null, c.meta, Modifier.padding(start = 8.dp), size = 18.dp)
            Trailing.NONE -> Unit
        }
    }
}

enum class Trailing { CHEVRON, EXTERNAL, NONE }

/** A row whose whole surface toggles (`Role.Switch`); the switch itself is decoration (design notes). */
@Composable
fun SwitchRow(
    @DrawableRes icon: Int?,
    title: String,
    supporting: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: (@Composable () -> Unit)? = null,
) {
    val c = Fg.colors
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 64.dp)
            .padding(start = Margin, end = Margin, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            FgIcon(icon, null, c.ink2)
            Spacer(Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            if (supportingContent != null) supportingContent() else {
                Text(supporting, Modifier.padding(top = 2.dp), color = c.meta, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.size(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.accent,
                checkedThumbColor = c.bg,
                uncheckedTrackColor = c.surface2,
                uncheckedBorderColor = c.outline,
                uncheckedThumbColor = c.outline,
            ),
        )
    }
}

/** Radio option row (design-system §5): name `bodyLarge` 600 + optional tag, supporting text, 22dp radio. */
@Composable
fun RadioRow(
    name: String,
    supporting: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: (@Composable () -> Unit)? = null,
) {
    val c = Fg.colors
    Row(
        modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 58.dp)
            .padding(start = Margin, end = Margin, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, color = c.ink, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                tag?.invoke()
            }
            if (supporting != null) Text(supporting, Modifier.padding(top = 2.dp), color = c.meta, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.size(12.dp))
        Box(
            Modifier.size(22.dp).border(2.dp, if (selected) c.accent else c.outline, CircleShape).testTag(if (selected) "radio-on" else "radio-off"),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(11.dp).background(c.accent, CircleShape))
        }
    }
}
