// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.work

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.digest.DigestMode
import io.github.ukpratik.founderguru.core.digest.DigestText
import io.github.ukpratik.founderguru.core.model.Lens
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.StoryRow
import io.github.ukpratik.founderguru.data.Tab
import io.github.ukpratik.founderguru.ui.FakeRefresher
import io.github.ukpratik.founderguru.ui.HOUR
import io.github.ukpratik.founderguru.ui.NOW
import io.github.ukpratik.founderguru.ui.settingsStore
import io.github.ukpratik.founderguru.ui.story
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** DigestWorker logic (LLD §8 "Workers"): skips when n = 0, posts when allowed, always reschedules, > 24 h late skips. */
class DigestRunnerTest {
    private class Poster(var enabled: Boolean = true) : DigestPoster {
        val posted = mutableListOf<Pair<DigestText, Tab>>()
        override fun canPost() = enabled
        override fun post(text: DigestText, tab: Tab) {
            posted += text to tab
        }
    }

    private class Scheduler : DigestScheduling {
        val next = mutableListOf<Pair<Long, DigestMode>>()
        override fun scheduleNextDigest(targetMs: Long, mode: DigestMode) {
            next += targetMs to mode
        }
    }

    private class Harness(scope: TestScope, var now: Long = NOW) {
        val settings = settingsStore(scope.backgroundScope)
        val rows = MutableStateFlow<List<StoryRow>>(emptyList())
        val refresher = FakeRefresher()
        val poster = Poster()
        val scheduler = Scheduler()
        var asked: Pair<Tab, Region>? = null
        val runner = DigestRunner(
            settings, refresher, { tab, region -> asked = tab to region; rows }, poster, scheduler,
            regionName = { if (it == Region.GLOBAL) null else "India" }, clock = { now },
        )
    }

    private val target = NOW - HOUR

    @Test fun `posts the count since the last visit, refreshing first, and reschedules from the target`() = runTest {
        val h = Harness(this)
        h.settings.completeOnboarding(Region.IN, DigestMode.WEEKLY)
        h.settings.setLastVisit(Tab.LOCAL, NOW - 10 * HOUR)
        h.rows.value = listOf(
            story(1, firstSeenAgo = 2 * HOUR, lenses = setOf(Lens.FUNDING)),
            story(2, firstSeenAgo = 3 * HOUR),
            story(3, firstSeenAgo = 20 * HOUR), // seen before the last visit
        )
        assertThat(h.runner.run(target)).isEqualTo(DigestRunner.Outcome.POSTED)
        assertThat(h.refresher.nowCalls).isEqualTo(1)
        assertThat(h.asked).isEqualTo(Tab.LOCAL to Region.IN)
        val (text, tab) = h.poster.posted.single()
        assertThat(tab).isEqualTo(Tab.LOCAL)
        assertThat(text.title).isEqualTo("This week in India")
        assertThat(text.body).isEqualTo("1 funding story · 2 new stories")
        assertThat(h.scheduler.next).containsExactly(target to DigestMode.WEEKLY)
        // Workers never write last_visit (US-008).
        assertThat(h.settings.current().lastVisitLocal).isEqualTo(NOW - 10 * HOUR)
    }

    @Test fun `global only reads the Global tab and says worldwide`() = runTest {
        val h = Harness(this)
        h.settings.completeOnboarding(Region.GLOBAL, DigestMode.DAILY)
        h.settings.setLastVisit(Tab.GLOBAL, NOW - 10 * HOUR)
        h.rows.value = listOf(story(1, firstSeenAgo = HOUR))
        assertThat(h.runner.run(target)).isEqualTo(DigestRunner.Outcome.POSTED)
        assertThat(h.asked).isEqualTo(Tab.GLOBAL to Region.GLOBAL)
        assertThat(h.poster.posted.single().first.title).isEqualTo("Today worldwide")
        assertThat(h.poster.posted.single().second).isEqualTo(Tab.GLOBAL)
    }

    @Test fun `nothing new skips the notification but reschedules`() = runTest {
        val h = Harness(this)
        h.settings.completeOnboarding(Region.IN, DigestMode.WEEKLY)
        h.settings.setLastVisit(Tab.LOCAL, NOW)
        h.rows.value = listOf(story(1, firstSeenAgo = HOUR))
        assertThat(h.runner.run(target)).isEqualTo(DigestRunner.Outcome.NOTHING_NEW)
        assertThat(h.poster.posted).isEmpty()
        assertThat(h.scheduler.next).hasSize(1)
    }

    @Test fun `offline refresh still digests from the cache`() = runTest {
        val h = Harness(this)
        h.refresher.result = null
        h.settings.completeOnboarding(Region.IN, DigestMode.WEEKLY)
        h.settings.setLastVisit(Tab.LOCAL, NOW - 10 * HOUR)
        h.rows.value = listOf(story(1, firstSeenAgo = HOUR))
        assertThat(h.runner.run(target)).isEqualTo(DigestRunner.Outcome.POSTED)
    }

    @Test fun `more than 24 h late is skipped without refreshing, and the next run is scheduled`() = runTest {
        val h = Harness(this)
        h.settings.completeOnboarding(Region.IN, DigestMode.WEEKLY)
        h.settings.setLastVisit(Tab.LOCAL, 0)
        h.rows.value = listOf(story(1))
        assertThat(h.runner.run(NOW - 25 * HOUR)).isEqualTo(DigestRunner.Outcome.TOO_LATE)
        assertThat(h.refresher.nowCalls).isEqualTo(0)
        assertThat(h.poster.posted).isEmpty()
        assertThat(h.scheduler.next).containsExactly(NOW - 25 * HOUR to DigestMode.WEEKLY)
        // Exactly 24 h late still posts.
        assertThat(h.runner.run(NOW - 24 * HOUR)).isEqualTo(DigestRunner.Outcome.POSTED)
    }

    @Test fun `blocked notifications store Off everywhere and stop the chain`() = runTest {
        val h = Harness(this)
        h.poster.enabled = false
        h.settings.completeOnboarding(Region.IN, DigestMode.DAILY)
        assertThat(h.runner.run(target)).isEqualTo(DigestRunner.Outcome.BLOCKED)
        assertThat(h.settings.current().digest).isEqualTo(DigestMode.OFF)
        assertThat(h.scheduler.next).isEmpty()
    }

    @Test fun `digest turned off meanwhile does nothing`() = runTest {
        val h = Harness(this)
        h.settings.completeOnboarding(Region.IN, DigestMode.OFF)
        assertThat(h.runner.run(target)).isEqualTo(DigestRunner.Outcome.OFF)
        assertThat(h.refresher.nowCalls).isEqualTo(0)
        assertThat(h.scheduler.next).isEmpty()
    }

    @Test fun `no recorded visit yet skips`() = runTest {
        val h = Harness(this)
        h.settings.completeOnboarding(Region.IN, DigestMode.WEEKLY)
        h.rows.value = listOf(story(1))
        assertThat(h.runner.run(target)).isEqualTo(DigestRunner.Outcome.NO_VISIT)
        assertThat(h.scheduler.next).hasSize(1)
    }
}
