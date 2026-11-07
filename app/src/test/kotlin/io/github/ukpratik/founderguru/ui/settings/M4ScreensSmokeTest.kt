// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.digest.DigestMode
import io.github.ukpratik.founderguru.core.model.FailureKind
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.data.prefs.ThemePref
import io.github.ukpratik.founderguru.ui.HOUR
import io.github.ukpratik.founderguru.ui.NOW
import io.github.ukpratik.founderguru.ui.components.StoryRowActions
import io.github.ukpratik.founderguru.ui.saved.SavedScreen
import io.github.ukpratik.founderguru.ui.saved.SavedUiState
import io.github.ukpratik.founderguru.ui.search.SearchScreen
import io.github.ukpratik.founderguru.ui.search.SearchUiState
import io.github.ukpratik.founderguru.ui.story
import io.github.ukpratik.founderguru.ui.theme.FounderGuruTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** M4 screens render with the design copy: Settings values, source health words, Saved empty/list, Search results. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class M4ScreensSmokeTest {
    @get:Rule val rule = createComposeRule()

    private val actions = StoryRowActions({}, { _, _ -> }, {}, {})

    @Test fun `settings home shows every value, blocked digest and the theme dialog`() {
        var theme: ThemePref? = null
        val state = SettingsUiState(Region.IN, ThemePref.SYSTEM, bgRefresh = false, digest = DigestMode.OFF, notificationsBlocked = true, sources = SourcesSummary(4, 7, 1, 1))
        rule.setContent { FounderGuruTheme(dark = false) { SettingsHomeScreen(state, "0.1.0", {}, {}, {}, {}, {}, { theme = it }) } }
        rule.onNodeWithText("India").assertExists()
        rule.onNodeWithText("4 India + 7 global · 1 couldn't update, 1 blocked").assertExists()
        rule.onNodeWithText("Off · notifications are blocked for FounderGuru").assertExists()
        rule.onNodeWithText("Off. Stories update only when you pull to refresh").assertExists()
        rule.onNodeWithText("System default").performClick()
        rule.onNodeWithTag("theme-DARK").performClick()
        rule.onNodeWithText("OK").performClick()
        assertThat(theme).isEqualTo(ThemePref.DARK)
    }

    @Test fun `sources show health in words`() {
        val items = listOf(
            SourceItem("a", "Inc42", true, Health.Ok(NOW - HOUR), null),
            SourceItem("b", "Entrackr", true, Health.Failed(FailureKind.TIMEOUT, null, NOW - HOUR, NOW - 3 * HOUR), null),
            SourceItem("c", "Rest of World", true, Health.Blocked(403, NOW - HOUR), null),
            SourceItem("d", "Product Hunt", false, Health.Off, null),
            SourceItem("e", "Show HN", true, Health.Ok(NOW - HOUR), "hnrss.org"),
        )
        val state = SourcesUiState(listOf(SourceGroup(Region.GLOBAL, items)), NOW - HOUR, Region.GLOBAL)
        rule.setContent { FounderGuruTheme(dark = true) { SourcesScreen(state, NOW, {}, { _, _ -> }) } }
        rule.onNodeWithText("GLOBAL · 4 OF 5 ON").assertExists()
        rule.onNodeWithText("Couldn't update · timed out", substring = true).assertExists()
        rule.onNodeWithText("Showing stories up to", substring = true).assertExists()
        rule.onNodeWithText("Blocked by publisher (HTTP 403)", substring = true).assertExists()
        rule.onNodeWithText("Off · stories hidden").assertExists()
        rule.onNodeWithText("via hnrss.org", substring = true).assertExists()
    }

    @Test fun `saved empty and list, search results and no results`() {
        var saved: SavedUiState by mutableStateOf<SavedUiState>(SavedUiState.Empty(offline = true))
        rule.setContent { FounderGuruTheme(dark = false) { SavedScreen(saved, NOW, "India", {}, {}, actions) } }
        rule.onNodeWithText("Nothing saved yet").assertExists()
        rule.onNodeWithText("Go to India").assertExists()
        rule.onNodeWithText("Offline · saved stories still open if your browser cached them").assertExists()
        saved = SavedUiState.Content(listOf(story(1, saved = true), story(2, saved = true)), offline = false)
        rule.onNodeWithText("2 stories").assertExists()
        rule.onNodeWithTag("story-1").assertExists()
    }

    @Test fun `search results header and no results`() {
        var state: SearchUiState by mutableStateOf<SearchUiState>(SearchUiState.Results("upi", listOf(story(1, title = "UPI credit lines"))))
        rule.setContent { FounderGuruTheme(dark = false) { SearchScreen("upi", state, offline = false, now = NOW, onQuery = {}, onClear = {}, onBack = {}, rowActions = actions) } }
        rule.onNodeWithText("1 story · newest first").assertExists()
        rule.onNodeWithTag("story-1").assertExists()
        state = SearchUiState.NoResults("zepto series g")
        rule.onNodeWithText("No stories match \"zepto series g\"").assertExists()
        rule.onNodeWithText("Clear search").assertExists()
    }
}
