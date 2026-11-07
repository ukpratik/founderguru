// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.nav

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.AppContainer
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.data.Tab
import io.github.ukpratik.founderguru.ui.theme.FounderGuruTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home navigation with the real container (D-016): four nav items, Local ↔ Global, the M4 stubs, Search as a child of
 * the tab. Onboarding is not completed here, so no first-sync refresh (no network) is started.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppNavTest {
    @get:Rule val rule = createComposeRule()

    private fun container(region: Region): AppContainer {
        val c = AppContainer(ApplicationProvider.getApplicationContext())
        runBlocking { c.settings.setRegion(region) }
        return c
    }

    @Test fun `bottom navigation moves between the tabs and stubs`() {
        val c = container(Region.IN)
        rule.setContent { FounderGuruTheme(dark = false) { MainScaffold(c, globalOnly = false, dark = false, message = null, onMessageShown = {}) } }
        listOf("LOCAL", "GLOBAL", "SAVED", "SETTINGS").forEach { rule.onNodeWithTag("nav-$it").assertExists() }
        rule.onNodeWithText("India").assertExists()

        rule.onNodeWithTag("nav-GLOBAL").performClick()
        rule.onNodeWithText("World").assertExists()
        rule.waitUntil(5_000) { c.visits.visit.value?.tab == Tab.GLOBAL }

        rule.onNodeWithContentDescription("Search stories").performClick()
        rule.onNodeWithText("Search arrives in the next build.").assertExists()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("World").assertExists()

        rule.onNodeWithTag("nav-SAVED").performClick()
        rule.onNodeWithText("Saved stories arrive in the next build.").assertExists()
        rule.waitUntil(5_000) { c.visits.visit.value == null }
        assertThat(runBlocking { c.settings.current().lastVisitGlobal }).isNotNull()
    }

    @Test fun `global only has three items and opens on Global`() {
        val c = container(Region.GLOBAL)
        rule.setContent { FounderGuruTheme(dark = true) { MainScaffold(c, globalOnly = true, dark = true, message = null, onMessageShown = {}) } }
        rule.onNodeWithTag("nav-LOCAL").assertDoesNotExist()
        rule.onNodeWithTag("nav-GLOBAL").assertExists()
        rule.onNodeWithText("FOUNDERGURU").assertExists()
    }
}
