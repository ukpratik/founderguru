// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.stub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.ukpratik.founderguru.R
import io.github.ukpratik.founderguru.ui.components.FgIcon
import io.github.ukpratik.founderguru.ui.components.Hairline
import io.github.ukpratik.founderguru.ui.components.Margin
import io.github.ukpratik.founderguru.ui.components.StatusBarSpacer
import io.github.ukpratik.founderguru.ui.theme.Fg

/**
 * Placeholder for the M4 destinations (Saved, Settings, Search) so the M3 navigation is complete. Replaced in M4;
 * the design for each is in product/04-design/final.
 */
@Composable
fun StubScreen(title: String, body: String, onBack: (() -> Unit)? = null) {
    val c = Fg.colors
    Column(Modifier.fillMaxSize().background(c.bg)) {
        StatusBarSpacer()
        if (onBack != null) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { FgIcon(R.drawable.ic_back, stringResource(R.string.back), c.ink2) }
            }
        }
        Text(
            title,
            Modifier.padding(start = Margin, end = Margin, top = 16.dp, bottom = 12.dp).semantics { heading() },
            color = c.ink,
            style = MaterialTheme.typography.headlineLarge,
        )
        Hairline(Modifier.padding(horizontal = Margin), color = c.ink)
        Text(body, Modifier.padding(Margin), color = c.ink2, style = MaterialTheme.typography.bodyMedium)
    }
}
