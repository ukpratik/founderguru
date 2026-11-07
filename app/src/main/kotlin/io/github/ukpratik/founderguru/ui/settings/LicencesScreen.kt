// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.settings

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ukpratik.founderguru.R
import io.github.ukpratik.founderguru.ui.components.BackBar
import io.github.ukpratik.founderguru.ui.components.ScreenTitle
import io.github.ukpratik.founderguru.ui.components.SettingsRow
import io.github.ukpratik.founderguru.ui.components.StatusBarSpacer
import io.github.ukpratik.founderguru.ui.components.Trailing
import io.github.ukpratik.founderguru.ui.theme.Fg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One shipped library, font or icon set and its licences, from the AboutLibraries report (US-013, ADR-007). */
data class LicenceEntry(val name: String, val version: String, val licences: List<Licence>)

data class Licence(val name: String, val text: String)

/**
 * Reads the report the AboutLibraries Gradle plugin writes at build time (`res/raw/aboutlibraries.json`, offline,
 * licence texts included). The same report is the strict licence gate of the release build, so this screen lists
 * exactly what ships. No AboutLibraries runtime library and no Google oss-licenses plugin (as in Lantern Sudoku).
 */
object Licences {
    fun parse(json: String): List<LicenceEntry> {
        val root = JSONObject(json)
        val texts = root.optJSONObject("licenses") ?: JSONObject()
        val libs = root.optJSONArray("libraries") ?: return emptyList()
        return (0 until libs.length()).map { i ->
            val lib = libs.getJSONObject(i)
            val ids = lib.optJSONArray("licenses")
            val licences = (0 until (ids?.length() ?: 0)).map { j ->
                val id = ids!!.getString(j)
                val l = texts.optJSONObject(id)
                Licence(
                    l?.optString("name")?.takeIf { it.isNotBlank() } ?: id,
                    l?.optString("content").orEmpty().ifBlank { l?.optString("url").orEmpty() },
                )
            }
            LicenceEntry(lib.optString("name").ifBlank { lib.optString("uniqueId") }, lib.optString("artifactVersion"), licences)
        }.sortedBy { it.name.lowercase() }
    }

    fun read(res: Resources): List<LicenceEntry> =
        parse(res.openRawResource(R.raw.aboutlibraries).bufferedReader().use { it.readText() })
}

/** About › Open-source licences: name, version and licence of everything shipped; a tap shows the licence text. */
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    val c = Fg.colors
    val res = LocalResources.current
    var entries by remember { mutableStateOf<List<LicenceEntry>?>(null) }
    LaunchedEffect(res) { entries = withContext(Dispatchers.IO) { Licences.read(res) } }
    var open by remember { mutableStateOf<LicenceEntry?>(null) }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        StatusBarSpacer()
        BackBar(stringResource(R.string.about_title), onBack)
        LazyColumn(Modifier.fillMaxSize().testTag("licences")) {
            item { ScreenTitle(stringResource(R.string.licences_title), stringResource(R.string.licences_intro)) }
            // No keys: a few artifacts share a display name and version (Compose multiplatform splits).
            items(entries.orEmpty()) { e ->
                val title = if (e.version.isBlank()) e.name else stringResource(R.string.licence_row, e.name, e.version)
                SettingsRow(null, title, e.licences.joinToString { it.name }, { open = e }, trailing = Trailing.NONE)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    open?.let { e ->
        AlertDialog(
            onDismissRequest = { open = null },
            containerColor = c.overlay,
            shape = MaterialTheme.shapes.large,
            title = { Text(e.name, color = c.ink, style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for (l in e.licences) {
                        Text(l.name, Modifier.padding(bottom = 6.dp), color = c.ink, style = MaterialTheme.typography.titleSmall)
                        Text(l.text, Modifier.padding(bottom = 12.dp), color = c.ink2, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = null }) {
                    Text(stringResource(R.string.licence_close), color = c.accent, style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }
}
