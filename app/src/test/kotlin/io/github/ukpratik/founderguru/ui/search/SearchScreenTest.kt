// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.search

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.ui.components.StoryRowActions
import io.github.ukpratik.founderguru.ui.story
import io.github.ukpratik.founderguru.ui.theme.FounderGuruTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** BUG-008 (US-014): a very long query is echoed in at most two lines; the search bar, Back and Clear stay on screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class SearchScreenTest {
    @get:Rule val rule = createComposeRule()

    private val actions = StoryRowActions(open = {}, setSaved = { _, _ -> }, share = {}, openInBrowser = {})
    private val longQuery = (1..200).joinToString(" ") { "inc42 ok 200 2026-11-07 20:43" }

    private fun show(state: SearchUiState) = rule.setContent {
        FounderGuruTheme(dark = false) { SearchScreen(longQuery, state, offline = false, now = 0L, onQuery = {}, onClear = {}, onBack = {}, rowActions = actions) }
    }

    private fun assertBarOnScreen() {
        rule.onNodeWithTag("search-field").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").assertIsDisplayed()
        rule.onNodeWithContentDescription("Clear search").assertIsDisplayed()
    }

    @Test fun `results echo a long query in at most two lines`() {
        show(SearchUiState.Results(longQuery, listOf(story(1))))
        val layouts = mutableListOf<TextLayoutResult>()
        val node = rule.onNodeWithTag("search-query").fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertThat(layouts.single().lineCount).isAtMost(2)
        assertBarOnScreen()
    }

    @Test fun `no results title shortens a long query`() {
        show(SearchUiState.NoResults(longQuery))
        assertThat(shortQuery(longQuery).length).isAtMost(61)
        assertThat(shortQuery(longQuery)).endsWith("…")
        assertThat(shortQuery("upi")).isEqualTo("upi")
        rule.onNodeWithText("No stories match \"${shortQuery(longQuery)}\"").assertExists()
        assertBarOnScreen()
    }
}
