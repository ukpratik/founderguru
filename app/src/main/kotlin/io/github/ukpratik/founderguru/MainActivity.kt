// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ukpratik.founderguru.data.Tab
import io.github.ukpratik.founderguru.platform.DeviceCountry
import io.github.ukpratik.founderguru.platform.DigestNotifier
import io.github.ukpratik.founderguru.ui.app.AppUiState
import io.github.ukpratik.founderguru.ui.app.AppViewModel
import io.github.ukpratik.founderguru.ui.nav.BlankFrame
import io.github.ukpratik.founderguru.ui.nav.MainScaffold
import io.github.ukpratik.founderguru.ui.onboarding.OnboardingScreen
import io.github.ukpratik.founderguru.ui.onboarding.OnboardingViewModel
import io.github.ukpratik.founderguru.ui.theme.DarkFg
import io.github.ukpratik.founderguru.ui.theme.FounderGuruTheme
import io.github.ukpratik.founderguru.ui.theme.LightFg
import io.github.ukpratik.founderguru.ui.theme.isDark

/** Single activity (LLD §5). System bars follow the app theme, not the system one (design-system §6). */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as FounderGuruApp).container
        if (savedInstanceState == null) handleDigestTap(intent)
        setContent {
            val app = viewModel { AppViewModel(container.settings, container.messages) }
            val theme by app.theme.collectAsStateWithLifecycle()
            val dark = theme.isDark()
            LaunchedEffect(dark) { applySystemBars(dark) }
            FounderGuruTheme(dark) {
                val state by app.uiState.collectAsStateWithLifecycle()
                val message by app.message.collectAsStateWithLifecycle()
                when (val s = state) {
                    AppUiState.Loading -> BlankFrame()
                    AppUiState.Onboarding -> OnboardingRoute(container)
                    // Switching to or from "Global only" rebuilds navigation (three or four tabs, another start tab).
                    is AppUiState.Main -> key(s.globalOnly) { MainScaffold(container, s.globalOnly, dark, message, app::messageShown) }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDigestTap(intent)
    }

    /** Foreground refresh triggers (D-022 OQ-A, D-017): stale cache on open, reconnect while visible. */
    override fun onStart() {
        super.onStart()
        (application as FounderGuruApp).container.foreground.onStart()
    }

    override fun onStop() {
        (application as FounderGuruApp).container.foreground.onStop()
        super.onStop()
    }

    /** Digest notification tap (US-012): open its tab with lens All, at the "New since" divider (LLD §4.9). */
    private fun handleDigestTap(intent: Intent?) {
        if (intent?.getBooleanExtra(DigestNotifier.EXTRA_FROM_DIGEST, false) != true) return
        val tab = intent.getStringExtra(DigestNotifier.EXTRA_OPEN_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name } } ?: Tab.LOCAL
        intent.removeExtra(DigestNotifier.EXTRA_FROM_DIGEST)
        (application as FounderGuruApp).container.openFromDigest(tab)
    }

    private fun applySystemBars(dark: Boolean) {
        val bg = (if (dark) DarkFg.bg else LightFg.bg).toArgb()
        val style = if (dark) SystemBarStyle.dark(bg) else SystemBarStyle.light(bg, bg)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}

@Composable
private fun OnboardingRoute(container: AppContainer) {
    val context = LocalContext.current
    val vm = viewModel { OnboardingViewModel(DeviceCountry.suggestedRegion(context), container.settings, container.messages) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val names = container.catalog.sources.groupBy({ it.region }, { it.name })
    OnboardingScreen(state, names, vm)
}
