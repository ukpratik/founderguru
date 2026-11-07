// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * M1 developer screen: runs the refresh pipeline and lists per-source health. Replaced by the real UI in M3
 * (design-system, onboarding, Local/Global); it intentionally uses no design tokens.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as FounderGuruApp).container
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val scope = rememberCoroutineScope()
                    var report by remember { mutableStateOf("") }
                    var running by remember { mutableStateOf(false) }
                    Column(Modifier.safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
                        Button(
                            enabled = !running,
                            onClick = {
                                running = true
                                scope.launch {
                                    container.sourcesRepository.sync(container.catalog)
                                    val r = container.refreshRepository.refresh(region = null)
                                    val states = container.db.sources().all().sortedBy { it.id }
                                    report = "Stories: ${container.db.stories().count()} · updated ${r.updated.size}, " +
                                        "304 ${r.notModified.size}, failed ${r.failed.size}\n\n" +
                                        states.joinToString("\n") { "${it.id}: ${it.lastStatus} ${it.lastError.orEmpty()}" }
                                    running = false
                                }
                            },
                        ) { Text(stringResource(R.string.dev_refresh)) }
                        Text(report, Modifier.padding(top = 16.dp))
                    }
                }
            }
        }
    }
}
