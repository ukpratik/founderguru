// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.feed

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.feed.Banner
import io.github.ukpratik.founderguru.core.feed.LensFilter
import io.github.ukpratik.founderguru.core.model.Lens
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.StoryRow
import io.github.ukpratik.founderguru.data.Tab
import io.github.ukpratik.founderguru.data.Visit
import io.github.ukpratik.founderguru.data.prefs.DigestMode
import io.github.ukpratik.founderguru.ui.FakeActions
import io.github.ukpratik.founderguru.ui.FakeConnectivity
import io.github.ukpratik.founderguru.ui.FakeRefresher
import io.github.ukpratik.founderguru.ui.HOUR
import io.github.ukpratik.founderguru.ui.NOW
import io.github.ukpratik.founderguru.ui.indiaAndGlobal
import io.github.ukpratik.founderguru.ui.settingsStore
import io.github.ukpratik.founderguru.ui.story
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Feed ViewModel state sequences (LLD §8 "ViewModels"): first sync → content, frozen anchor, lens, banners. */
@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private class Harness(scope: TestScope) {
        val settings = settingsStore(scope.backgroundScope)
        val rows = MutableStateFlow<List<StoryRow>>(emptyList())
        val sources = MutableStateFlow(indiaAndGlobal().map { it.copy(lastStatus = null, lastAttemptAt = null) })
        val connectivity = FakeConnectivity()
        val refresher = FakeRefresher()
        val visits = MutableStateFlow<Visit?>(null)
        val actions = FakeActions()
        var regionAsked: Region? = null
        val vm = FeedViewModel(
            Tab.LOCAL, settings, { _, region -> regionAsked = region; rows }, sources, connectivity, refresher, visits, actions, { NOW },
        )
    }

    @Test fun `first sync starts a refresh, shows the skeleton, then the stories`() = runTest(dispatcher) {
        val h = Harness(this)
        h.vm.uiState.test {
            assertThat(awaitItem()).isEqualTo(FeedUiState.Loading)
            h.settings.completeOnboarding(Region.IN, DigestMode.OFF)
            h.visits.value = Visit(Tab.LOCAL, anchor = null, seq = 1)
            h.refresher.refreshing.value = true
            assertThat(expectMostRecentItem()).isInstanceOf(FeedUiState.FirstSync::class.java)
            assertThat(h.refresher.calls).isEqualTo(1)
            assertThat(h.regionAsked).isEqualTo(Region.IN)

            h.rows.value = listOf(story(1), story(2))
            h.sources.value = indiaAndGlobal()
            h.settings.setLastRefreshAt(NOW)
            h.refresher.refreshing.value = false
            val content = expectMostRecentItem() as FeedUiState.Content
            assertThat(content.sections.earlier.map { it.id }).containsExactly(1L, 2L)
            assertThat(content.dateline).isEqualTo(DatelineStatus.Updated(NOW, offline = false))
            cancelAndIgnoreRemainingEvents()
        }
        // A completed first sync does not trigger again.
        assertThat(h.refresher.calls).isEqualTo(1)
    }

    @Test fun `the anchor stays frozen while a refresh adds stories`() = runTest(dispatcher) {
        val h = Harness(this)
        h.settings.setLastRefreshAt(NOW - HOUR) // before onboarding completes, so no first-sync refresh fires
        h.settings.completeOnboarding(Region.IN, DigestMode.OFF)
        h.sources.value = indiaAndGlobal()
        h.rows.value = listOf(story(1, firstSeenAgo = HOUR), story(2, publishedAgo = 10 * HOUR, firstSeenAgo = 10 * HOUR))
        h.visits.value = Visit(Tab.LOCAL, anchor = NOW - 2 * HOUR, seq = 1)
        h.vm.uiState.test {
            var s = expectMostRecentItem() as FeedUiState.Content
            assertThat(s.sections.new.map { it.id }).containsExactly(1L)
            // Refresh during the visit: a story first seen now joins the new block; the anchor does not move.
            h.rows.value = h.rows.value + story(3, publishedAgo = 0, firstSeenAgo = 0)
            s = expectMostRecentItem() as FeedUiState.Content
            assertThat(s.sections.new.map { it.id }).containsExactly(3L, 1L).inOrder()
            assertThat(s.sections.anchor).isEqualTo(NOW - 2 * HOUR)
            // Another tab's visit does not change this tab's anchor.
            h.visits.value = Visit(Tab.GLOBAL, anchor = NOW, seq = 2)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(h.refresher.calls).isEqualTo(0)
    }

    @Test fun `lens choice is stored per tab`() = runTest(dispatcher) {
        val h = Harness(this)
        h.settings.completeOnboarding(Region.IN, DigestMode.OFF)
        h.settings.setLastRefreshAt(NOW - HOUR)
        h.sources.value = indiaAndGlobal()
        h.rows.value = listOf(story(1, lenses = setOf(Lens.FUNDING)), story(2))
        h.visits.value = Visit(Tab.LOCAL, anchor = null, seq = 1)
        h.vm.uiState.test {
            expectMostRecentItem()
            h.vm.selectLens(LensFilter.Only(Lens.FUNDING))
            val s = expectMostRecentItem() as FeedUiState.Content
            assertThat(s.lens).isEqualTo(LensFilter.Only(Lens.FUNDING))
            assertThat(s.sections.earlier.map { it.id }).containsExactly(1L)
            cancelAndIgnoreRemainingEvents()
        }
        val stored = h.settings.settings.first()
        assertThat(stored.lensLocal).isEqualTo(LensFilter.Only(Lens.FUNDING))
        assertThat(stored.lensGlobal).isEqualTo(LensFilter.All)
    }

    @Test fun `dismissing banners and row actions`() = runTest(dispatcher) {
        val h = Harness(this)
        h.settings.completeOnboarding(Region.SEA, DigestMode.OFF)
        h.vm.dismiss(Banner.LimitedCoverage)
        h.vm.dismiss(Banner.SourcesFailed(2))
        val s = h.settings.settings.first { it.limitedBannerDismissed.isNotEmpty() && it.failedNoteDismissedAt > 0 }
        assertThat(s.limitedBannerDismissed).containsExactly(Region.SEA)
        assertThat(s.failedNoteDismissedAt).isEqualTo(NOW)
        h.vm.setSaved(story(7), true)
        h.vm.markOpened(story(7))
        assertThat(h.actions.saved).containsExactly(7L, true)
        assertThat(h.actions.opened).containsExactly(7L)
    }

    @Test fun `see Global carries the lens`() = runTest(dispatcher) {
        val h = Harness(this)
        h.vm.carryLensToGlobal(LensFilter.Only(Lens.PLAYBOOKS))
        assertThat(h.settings.settings.first { it.lensGlobal != LensFilter.All }.lensGlobal).isEqualTo(LensFilter.Only(Lens.PLAYBOOKS))
    }
}
