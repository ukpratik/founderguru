// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.nav

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.AppContainer
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.data.Tab
import io.github.ukpratik.founderguru.data.UserMessage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.ukpratik.founderguru.ui.theme.FounderGuruTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home navigation with the real container (D-016): four nav items, Local ↔ Global, Saved, Settings › About, Search as
 * a child of the tab. Onboarding is not completed here, so no first-sync refresh (no network) is started.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppNavTest {
    @get:Rule val rule = createComposeRule()

    private fun container(region: Region): AppContainer {
        val context = ApplicationProvider.getApplicationContext<Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        val c = AppContainer(context)
        runBlocking { c.settings.setRegion(region) }
        return c
    }

    @Test fun `bottom navigation moves between the tabs, Saved and Settings`() {
        val c = container(Region.IN)
        rule.setContent { FounderGuruTheme(dark = false) { MainScaffold(c, globalOnly = false, dark = false, message = null, onMessageShown = {}) } }
        listOf("LOCAL", "GLOBAL", "SAVED", "SETTINGS").forEach { rule.onNodeWithTag("nav-$it").assertExists() }
        rule.onNodeWithText("India").assertExists()

        rule.onNodeWithTag("nav-GLOBAL").performClick()
        rule.onNodeWithText("World").assertExists()
        rule.waitUntil(5_000) { c.visits.visit.value?.tab == Tab.GLOBAL }

        rule.onNodeWithContentDescription("Search stories").performClick()
        rule.onNodeWithText("Find a company or topic").assertExists()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("World").assertExists()

        rule.onNodeWithTag("nav-SAVED").performClick()
        rule.onNodeWithText("Nothing saved yet").assertExists()
        rule.waitUntil(5_000) { c.visits.visit.value == null }
        assertThat(runBlocking { c.settings.current().lastVisitGlobal }).isNotNull()

        rule.onNodeWithTag("nav-SETTINGS").performClick()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Region").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("settings").performScrollToNode(hasText("About FounderGuru"))
        rule.onNodeWithText("About FounderGuru").performClick()
        rule.onNodeWithTag("about").performScrollToNode(hasText("hnrss.org (third-party proxy for Show HN)", substring = true))
        rule.onNodeWithTag("about").performScrollToNode(hasText("Copy diagnostics"))
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("About FounderGuru").assertExists()
        rule.onNodeWithTag("nav-SETTINGS").assertIsSelected()
    }

    /** BUG-002 (US-011 AC 4): the feed's source links ("View sources", "Turn sources on") open Settings › Sources. */
    @Test fun `source links open Settings Sources directly`() {
        val c = container(Region.IN)
        runBlocking {
            c.startup.join()
            c.catalog.sources.forEach { c.db.sources().setEnabled(it.id, false) }
        }
        rule.setContent { FounderGuruTheme(dark = false) { MainScaffold(c, globalOnly = false, dark = false, message = null, onMessageShown = {}) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Turn sources on").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Turn sources on").performClick()
        rule.onNodeWithTag("sources").assertExists()
        rule.onNodeWithTag("nav-SETTINGS").assertIsSelected()
        // Back goes to the Settings home, as if the user had tapped Sources there.
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithTag("settings").assertExists()
    }

    /** BUG-003 (US-012): the one-time "Digest is off" snackbar shows although the message is cleared as it starts. */
    @Test fun `digest off snackbar shows once`() {
        val c = container(Region.IN)
        val shown = mutableListOf<UserMessage>()
        rule.setContent {
            var message by remember { mutableStateOf<UserMessage?>(UserMessage.DIGEST_OFF) }
            FounderGuruTheme(dark = false) {
                MainScaffold(c, globalOnly = false, dark = false, message = message, onMessageShown = { shown += it; message = null })
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Digest is off. Turn on in Settings").fetchSemanticsNodes().isNotEmpty() }
        assertThat(shown).containsExactly(UserMessage.DIGEST_OFF)
    }

    @Test fun `global only has three items and opens on Global`() {
        val c = container(Region.GLOBAL)
        rule.setContent { FounderGuruTheme(dark = true) { MainScaffold(c, globalOnly = true, dark = true, message = null, onMessageShown = {}) } }
        rule.onNodeWithTag("nav-LOCAL").assertDoesNotExist()
        rule.onNodeWithTag("nav-GLOBAL").assertExists()
        rule.onNodeWithText("FOUNDERGURU").assertExists()
    }
}
