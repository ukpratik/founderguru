// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.ukpratik.founderguru.platform.DeviceCountry
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
                    is AppUiState.Main -> MainScaffold(container, s.globalOnly, dark, message, app::messageShown)
                }
            }
        }
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
