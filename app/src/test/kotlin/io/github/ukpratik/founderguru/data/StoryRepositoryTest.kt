// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.model.Lens
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.SourceDef
import io.github.ukpratik.founderguru.core.model.SourcesCatalog
import io.github.ukpratik.founderguru.data.db.FgDatabase
import io.github.ukpratik.founderguru.data.db.StoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tab windows, via-Global blending and re-tagging on in-memory Room (US-002, US-003, LLD §3, §4.8, §4.10). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoryRepositoryTest {
    private val now = 100L * 24 * 60 * 60 * 1000
    private val hour = 60L * 60 * 1000
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FgDatabase::class.java)
        .allowMainThreadQueries().build()
    private val sources = listOf(
        SourceDef("in-a", "A", Region.IN, "https://a.in/f", "https://a.in"),
        SourceDef("in-b", "B", Region.IN, "https://b.in/f", "https://b.in"),
        SourceDef("eu-a", "E", Region.EU, "https://e.eu/f", "https://e.eu"),
        SourceDef("gl-a", "G", Region.GLOBAL, "https://g.com/f", "https://g.com", defaultLens = Lens.FUNDING),
    )
    private val catalog = SourcesCatalog(1, 1, sources)
    private val repo = StoryRepository(db, catalog) { now }
    private var next = 0

    @Before fun setUp() {
        runBlocking { SourcesRepository(db).sync(catalog) }
    }

    @After fun tearDown() = db.close()

    private fun story(source: String, ageHours: Long, mentions: Int = 0, title: String = "Story ${next + 1}", snippet: String = "") = runBlocking {
        next++
        db.stories().insert(
            StoryEntity(
                sourceId = source, sourceName = source, url = "https://x/$next", urlKey = "x/$next", title = title, titleKey = "k$next",
                snippet = snippet, imageUrl = null, publishedAt = now - ageHours * hour, firstSeenAt = now, regionMentions = mentions,
            ),
        )
    }

    @Test fun `Global tab - Global sources only, 14-day window, newest first`() = runBlocking<Unit> {
        story("gl-a", 1)
        story("gl-a", 30)
        story("gl-a", 15 * 24) // outside the window
        story("in-a", 2)
        val rows = repo.tabWindow(Tab.GLOBAL, Region.IN).first()
        assertThat(rows.map { it.publishedAt }).containsExactly(now - hour, now - 30 * hour).inOrder()
        assertThat(rows.none { it.viaGlobal }).isTrue()
    }

    @Test fun `Local blends at most 30 percent via-Global stories that mention the region`() = runBlocking<Unit> {
        repeat(7) { story("in-a", it.toLong() + 1) }
        repeat(5) { story("gl-a", it.toLong() + 1, mentions = Region.IN.mentionBit) }
        story("gl-a", 1, mentions = Region.EU.mentionBit) // mentions another region
        story("eu-a", 1)
        val rows = repo.tabWindow(Tab.LOCAL, Region.IN).first()
        assertThat(rows).hasSize(10)
        assertThat(rows.count { it.viaGlobal }).isEqualTo(3)
        assertThat(rows.filter { it.viaGlobal }.all { it.sourceId == "gl-a" }).isTrue()
        assertThat(rows.map { it.publishedAt }).isInOrder(Comparator.reverseOrder<Long>())
    }

    @Test fun `empty region gets no via stories, and disabled sources are hidden at once`() = runBlocking<Unit> {
        story("gl-a", 1, mentions = Region.IN.mentionBit)
        assertThat(repo.tabWindow(Tab.LOCAL, Region.IN).first()).isEmpty()
        repeat(7) { story("in-a", 1) }
        assertThat(repo.tabWindow(Tab.LOCAL, Region.IN).first().count { it.viaGlobal }).isEqualTo(1)
        db.sources().setEnabled("in-a", false)
        assertThat(repo.tabWindow(Tab.LOCAL, Region.IN).first()).isEmpty()
        db.sources().setEnabled("in-a", true)
        db.sources().setEnabled("gl-a", false)
        assertThat(repo.tabWindow(Tab.LOCAL, Region.IN).first().none { it.viaGlobal }).isTrue()
    }

    @Test fun `Global only users see Global rows for Local`() = runBlocking<Unit> {
        story("gl-a", 1)
        assertThat(repo.tabWindow(Tab.LOCAL, Region.GLOBAL).first().map { it.sourceId }).containsExactly("gl-a")
    }

    @Test fun `retag recomputes lenses, sectors and region mentions`() = runBlocking<Unit> {
        story("gl-a", 1, title = "Mumbai fintech startup launches a card")
        story("in-a", 1, title = "Lagos edtech lays off staff")
        assertThat(repo.retagAll()).isEqualTo(2)
        val g = db.stories().bySource("gl-a").single()
        assertThat(g.lenses).isEqualTo(Lens.FUNDING.bit or Lens.LAUNCHES.bit)
        assertThat(g.regionMentions).isEqualTo(Region.IN.mentionBit)
        assertThat(g.sectors).isNotEqualTo(0L)
        val i = db.stories().bySource("in-a").single()
        assertThat(i.lenses).isEqualTo(Lens.TRENDS.bit)
        assertThat(i.regionMentions).isEqualTo(0)
    }

    @Test fun `Saved - newest saved first, kept when the source is off, restore puts it back in place`() = runBlocking<Unit> {
        val a = story("in-a", 1)
        val b = story("gl-a", 2)
        val c = story("in-b", 3)
        db.stories().setSavedAt(a, 10)
        db.stories().setSavedAt(b, 30)
        db.stories().setSavedAt(c, 20)
        db.sources().setEnabled("gl-a", false)
        assertThat(repo.saved().first().map { it.id }).containsExactly(b, c, a).inOrder()

        repo.setSaved(c, false)
        assertThat(repo.saved().first().map { it.id }).containsExactly(b, a).inOrder()
        repo.restoreSavedAt(c, 20)
        assertThat(repo.saved().first().map { it.id }).containsExactly(b, c, a).inOrder()
    }

    @Test fun `Search - title and snippet, ASCII case-insensitive, newest first, saved always, disabled sources hidden`() = runBlocking<Unit> {
        val old = story("in-a", 5, title = "UPI credit lines")
        val new = story("in-b", 1, title = "Payroll startup", snippet = "Wages early over upi")
        val off = story("gl-a", 2, title = "UPI in Brazil")
        val offSaved = story("gl-a", 3, title = "UPI abroad")
        story("in-a", 1, title = "Unrelated")
        db.stories().setSavedAt(offSaved, 1)
        db.sources().setEnabled("gl-a", false)
        assertThat(repo.search("upi").first().map { it.id }).containsExactly(new, offSaved, old).inOrder()
        assertThat(off).isNotIn(repo.search("UPI").first().map { it.id })
    }

    @Test fun `Search escapes LIKE wildcards`() = runBlocking<Unit> {
        val pct = story("in-a", 1, title = "Growth of 50% in a year")
        story("in-a", 2, title = "Growth of 500 users")
        val under = story("in-a", 3, title = "snake_case naming")
        story("in-a", 4, title = "snakeXcase naming")
        assertThat(repo.search("50%").first().map { it.id }).containsExactly(pct)
        assertThat(repo.search("e_c").first().map { it.id }).containsExactly(under)
        assertThat(StoryRepository.likePattern("  a\\b%_ ")).isEqualTo("%a\\\\b\\%\\_%")
    }
}
