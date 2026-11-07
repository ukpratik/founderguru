// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.feed.Banner
import io.github.ukpratik.founderguru.core.feed.CaughtUpMarker
import io.github.ukpratik.founderguru.core.feed.FeedSections
import io.github.ukpratik.founderguru.core.feed.LensFilter
import io.github.ukpratik.founderguru.core.feed.SectorCount
import io.github.ukpratik.founderguru.core.model.Lens
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.tag.Sector
import io.github.ukpratik.founderguru.data.Tab
import io.github.ukpratik.founderguru.ui.components.StoryRow
import io.github.ukpratik.founderguru.ui.components.StoryRowActions
import io.github.ukpratik.founderguru.ui.feed.DatelineStatus
import io.github.ukpratik.founderguru.ui.feed.EmptyReason
import io.github.ukpratik.founderguru.ui.feed.FeedCallbacks
import io.github.ukpratik.founderguru.ui.feed.FeedHeader
import io.github.ukpratik.founderguru.ui.feed.FeedScreen
import io.github.ukpratik.founderguru.ui.feed.FeedUiState
import io.github.ukpratik.founderguru.ui.theme.FounderGuruTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose smoke tests (LLD §8): row semantics and custom actions, 48dp targets, the 200% layout switch, every state. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class ComposeSmokeTest {
    @get:Rule val rule = createComposeRule()

    private val saved = mutableListOf<Pair<Long, Boolean>>()
    private val opened = mutableListOf<Long>()
    private val shared = mutableListOf<Long>()
    private val actions = StoryRowActions(
        open = { opened += it.id },
        setSaved = { r, s -> saved += r.id to s },
        share = { shared += it.id },
        openInBrowser = { opened += it.id },
    )

    private fun content(fontScale: Float = 1f, body: @Composable () -> Unit) = rule.setContent {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) { FounderGuruTheme(dark = false) { body() } }
    }

    @Test fun `story row is one TalkBack stop with custom actions`() {
        val row = story(1, title = "Mandiwala raises Series B", lenses = setOf(Lens.FUNDING, Lens.TRENDS), via = true, saved = true)
        content { StoryRow(row, NOW, older = false, actions = actions) }
        val node = rule.onNodeWithTag("story-1").fetchSemanticsNode()
        val description = node.config[SemanticsProperties.ContentDescription].single()
        assertThat(description).isEqualTo("Mandiwala raises Series B. Inc42, 1 hour ago. Funding, Problems & trends. Via Global. Saved.")
        val custom = node.config[SemanticsActions.CustomActions].map { it.label }
        assertThat(custom).containsExactly("Remove from saved", "Share", "Open in browser").inOrder()
        node.config[SemanticsActions.CustomActions].first { it.label == "Remove from saved" }.action()
        node.config[SemanticsActions.CustomActions].first { it.label == "Share" }.action()
        assertThat(saved).containsExactly(1L to false)
        assertThat(shared).containsExactly(1L)
        // The bookmark and overflow are not separate focus stops.
        assertThat(node.children).isEmpty()
        rule.onNodeWithTag("story-1").performClick()
        assertThat(opened).containsExactly(1L)
    }

    @Test fun `thumbnail sits beside the text normally and below it at 200 percent`() {
        val withImage = story(1, image = "https://example.com/a.jpg", title = "Short")
        val without = story(2, title = "Short")
        content(fontScale = 2f) {
            Column {
                StoryRow(withImage, NOW, older = false, actions = actions)
                StoryRow(without, NOW, older = false, actions = actions)
            }
        }
        val h1 = rule.onNodeWithTag("story-1").fetchSemanticsNode().size.height
        val h2 = rule.onNodeWithTag("story-2").fetchSemanticsNode().size.height
        // Stacked below the snippet, the 76dp box adds at least its own height.
        assertThat((h1 - h2) / rule.density.density).isAtLeast(76f)
    }

    @Test fun `at 100 percent the thumbnail adds less than its own height`() {
        val withImage = story(1, image = "https://example.com/a.jpg", title = "Short")
        val without = story(2, title = "Short")
        content {
            Column {
                StoryRow(withImage, NOW, older = false, actions = actions)
                StoryRow(without, NOW, older = false, actions = actions)
            }
        }
        val h1 = rule.onNodeWithTag("story-1").fetchSemanticsNode().size.height
        val h2 = rule.onNodeWithTag("story-2").fetchSemanticsNode().size.height
        assertThat((h1 - h2) / rule.density.density).isLessThan(76f)
    }

    private val callbacks = FeedCallbacks({}, {}, {}, {}, {}, {}, {}, actions)
    private val india = FeedHeader(Tab.LOCAL, Region.IN)

    private fun feed(state: FeedUiState, fontScale: Float = 1f) = content(fontScale) { FeedScreen(state, NOW, callbacks) }

    @Test fun `content renders sections, hot strip and 48dp chips`() {
        val rows = listOf(story(1, lenses = setOf(Lens.FUNDING)), story(2, publishedAgo = 30 * HOUR))
        val sections = FeedSections(LensFilter.All, rows.take(1), rows.drop(1), CaughtUpMarker.AFTER_NEW, NOW - 2 * HOUR)
        val hot = listOf(SectorCount(Sector.FINTECH, 14), SectorCount(Sector.AI, 11), SectorCount(Sector.HEALTH, 3))
        feed(FeedUiState.Content(india, DatelineStatus.Updated(NOW - 6 * 60_000, false), LensFilter.All, hot, sections, emptyList(), false))
        rule.onNodeWithText("Local".uppercase()).assertExists()
        rule.onNodeWithText("India").assertExists()
        rule.onNodeWithText("Updated 6 min ago").assertExists()
        rule.onNodeWithContentDescription("Fintech, 14 stories this week").assertExists()
        rule.onNodeWithText("NEW SINCE YOUR LAST VISIT · 1").assertExists()
        rule.onNodeWithTag("lens-${LensFilter.Only(Lens.FUNDING)}").assertTouchHeightIsEqualTo(48.dp)
        rule.onNodeWithTag("feed").performScrollToNode(hasText("EARLIER"))
        rule.onNodeWithText("You're caught up").assertExists()
    }

    @Test fun `first sync shows skeleton and disabled chips`() {
        feed(FeedUiState.FirstSync(india, DatelineStatus.Getting(11), LensFilter.All))
        rule.onNodeWithText("Getting stories from 11 sources…").assertExists()
        rule.onNodeWithContentDescription("Loading stories").assertExists()
        val chip = rule.onNodeWithTag("lens-${LensFilter.All}").fetchSemanticsNode()
        assertThat(chip.config.getOrNull(SemanticsProperties.Disabled)).isNotNull()
    }

    @Test fun `first launch offline`() {
        feed(FeedUiState.FirstSyncOffline(india))
        rule.onNodeWithText("You're offline").assertExists()
        rule.onNodeWithText("Retry").assertExists()
    }

    @Test fun `all failed on Local offers Global`() {
        feed(FeedUiState.AllFailed(india, 4))
        rule.onNodeWithText("Couldn't reach India's sources").assertExists()
        rule.onNodeWithText("None of the 4 India sources answered. It's usually temporary; Global may still work.").assertExists()
        rule.onNodeWithText("See Global").assertExists()
    }

    @Test fun `empty lens on Local and on Global`() {
        feed(FeedUiState.Empty(india, DatelineStatus.Updated(NOW, false), EmptyReason.LENS_EMPTY, LensFilter.Only(Lens.PLAYBOOKS), emptyList(), emptyList(), 4, false))
        rule.onNodeWithText("No playbook stories in India").assertExists()
        rule.onNodeWithText("See Global").assertExists()
        rule.onNodeWithText("Show all stories").assertExists()
    }

    @Test fun `empty lens on Global offers show all only (G-3)`() {
        val global = FeedHeader(Tab.GLOBAL, Region.IN)
        feed(FeedUiState.Empty(global, DatelineStatus.Updated(NOW, false), EmptyReason.LENS_EMPTY, LensFilter.Only(Lens.PLAYBOOKS), emptyList(), emptyList(), 7, false))
        rule.onNodeWithText("World").assertExists()
        rule.onNodeWithText("No playbook stories this fortnight").assertExists()
        rule.onNodeWithText("See Global").assertDoesNotExist()
    }

    @Test fun `empty region and all sources off`() {
        feed(FeedUiState.Empty(india, DatelineStatus.Updated(NOW, false), EmptyReason.ALL_SOURCES_OFF, LensFilter.All, emptyList(), emptyList(), 0, false))
        rule.onNodeWithText("All India sources are off").assertExists()
        rule.onNodeWithText("Turn sources on").assertExists()
    }

    @Test fun `banners, offline strip and limited coverage`() {
        val sea = FeedHeader(Tab.LOCAL, Region.SEA)
        val banners = listOf(Banner.Offline(NOW - 2 * HOUR), Banner.LimitedCoverage, Banner.SourcesFailed(3))
        feed(FeedUiState.Empty(sea, DatelineStatus.Updated(NOW - 2 * HOUR, true), EmptyReason.NO_STORIES_IN_REGION, LensFilter.All, emptyList(), banners, 3, false))
        rule.onNodeWithText("Offline · showing stories from", substring = true).assertExists()
        rule.onNodeWithText("Limited coverage.", substring = true).assertExists()
        rule.onNodeWithText("3 sources couldn't update.", substring = true).assertExists()
        rule.onNodeWithText("No stories from Southeast Asia yet").assertExists()
    }

    @Test fun `global only header`() {
        val rows = listOf(story(1))
        val sections = FeedSections(LensFilter.All, emptyList(), rows, CaughtUpMarker.END_OF_LIST, null)
        feed(FeedUiState.Content(FeedHeader(Tab.GLOBAL, Region.GLOBAL), DatelineStatus.Updated(NOW, false), LensFilter.All, emptyList(), sections, emptyList(), false))
        rule.onNodeWithText("FOUNDERGURU").assertExists()
        rule.onNodeWithText("Global").assertExists()
        rule.onNodeWithText("That's everything new since your first sync.").assertExists()
    }

    @Test fun `200 percent text still renders the feed`() {
        val rows = listOf(story(1, image = "https://example.com/a.jpg"))
        val sections = FeedSections(LensFilter.All, emptyList(), rows, CaughtUpMarker.END_OF_LIST, null)
        feed(FeedUiState.Content(india, DatelineStatus.Updated(NOW, false), LensFilter.All, emptyList(), sections, emptyList(), false), fontScale = 2f)
        rule.onNodeWithTag("story-1").assertExists()
        rule.onNodeWithTag("lens-${LensFilter.All}").assertTouchHeightIsEqualTo(48.dp)
    }
}

