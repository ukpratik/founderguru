// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.feed

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.model.Lens
import io.github.ukpratik.founderguru.core.model.StoryRow
import org.junit.Test

private fun row(id: Long, at: Long, source: String = "s", lenses: Int = 0, firstSeen: Long = at) = StoryRow(
    id = id, sourceId = source, sourceName = source, url = "https://x/$id", title = "t$id", snippet = "", imageUrl = null,
    publishedAt = at, firstSeenAt = firstSeen, lenses = lenses, sectors = 0, regionMentions = 0, savedAt = null, openedAt = null,
)

class BlenderTest {
    @Test fun `cap maths keeps via at or under 30 percent`() {
        for (r in 0..200) {
            val via = Blender.maxVia(r)
            if (r + via > 0) assertThat(via.toDouble() / (r + via)).isAtMost(0.30)
        }
        assertThat(Blender.maxVia(7)).isEqualTo(3)
        assertThat(Blender.maxVia(6)).isEqualTo(2)
        assertThat(Blender.maxVia(2)).isEqualTo(0)
    }

    @Test fun `empty region gets no via stories (strict cap)`() {
        assertThat(Blender.blend(emptyList(), listOf(row(1, 10), row(2, 20)))).isEmpty()
    }

    @Test fun `takes the newest via candidates, marks them, merges newest first`() {
        val regional = (1L..7L).map { row(it, it * 10) }
        val via = listOf(row(101, 5), row(102, 75), row(103, 35), row(104, 55))
        val out = Blender.blend(regional, via)
        assertThat(out.filter { it.viaGlobal }.map { it.id }).containsExactly(102L, 104L, 103L).inOrder()
        assertThat(out.map { it.publishedAt }).isInOrder(Comparator.reverseOrder<Long>())
        assertThat(out).hasSize(10)
    }
}

class FeedAssemblerTest {
    private val rows = listOf(
        row(1, 100, lenses = Lens.FUNDING.bit, firstSeen = 500),
        row(2, 300, lenses = Lens.LAUNCHES.bit, firstSeen = 500),
        row(3, 200, lenses = Lens.FUNDING.bit, firstSeen = 50),
        row(4, 400, lenses = 0, firstSeen = 40),
    )

    @Test fun `first visit - one list, marker at the end`() {
        val s = FeedAssembler.assemble(rows, LensFilter.All, anchor = null)
        assertThat(s.new).isEmpty()
        assertThat(s.earlier.map { it.id }).containsExactly(4L, 2L, 3L, 1L).inOrder()
        assertThat(s.marker).isEqualTo(CaughtUpMarker.END_OF_LIST)
    }

    @Test fun `new since last visit uses firstSeenAt, both sections newest first`() {
        val s = FeedAssembler.assemble(rows, LensFilter.All, anchor = 100)
        assertThat(s.new.map { it.id }).containsExactly(2L, 1L).inOrder()
        assertThat(s.earlier.map { it.id }).containsExactly(4L, 3L).inOrder()
        assertThat(s.newCount).isEqualTo(2)
        assertThat(s.marker).isEqualTo(CaughtUpMarker.AFTER_NEW)
    }

    @Test fun `nothing new - marker at the top (D-023 G-1)`() {
        val s = FeedAssembler.assemble(rows, LensFilter.All, anchor = 1_000)
        assertThat(s.new).isEmpty()
        assertThat(s.marker).isEqualTo(CaughtUpMarker.TOP_NOTHING_NEW)
        assertThat(s.anchor).isEqualTo(1_000)
    }

    @Test fun `lens filter applies to both sections and is named in the result`() {
        val s = FeedAssembler.assemble(rows, LensFilter.Only(Lens.FUNDING), anchor = 100)
        assertThat(s.new.map { it.id }).containsExactly(1L)
        assertThat(s.earlier.map { it.id }).containsExactly(3L)
        assertThat(s.lens).isEqualTo(LensFilter.Only(Lens.FUNDING))
    }

    private fun input(
        window: Int = 5, filtered: Int = 5, last: Long? = 1L, online: Boolean = true, enabled: Int = 4, failed: Int = 0, refreshed: Boolean = true,
    ) = FeedAssembler.StateInput(window, filtered, last, online, enabled, failed, refreshed)

    @Test fun `every US-011 state`() {
        assertThat(FeedAssembler.chooseState(input())).isEqualTo(FeedState.CONTENT)
        assertThat(FeedAssembler.chooseState(input(window = 0, last = null, refreshed = false))).isEqualTo(FeedState.FIRST_SYNC)
        assertThat(FeedAssembler.chooseState(input(window = 0, last = null, online = false, refreshed = false))).isEqualTo(FeedState.FIRST_SYNC_OFFLINE)
        assertThat(FeedAssembler.chooseState(input(window = 0, last = null, failed = 4))).isEqualTo(FeedState.ALL_FAILED)
        assertThat(FeedAssembler.chooseState(input(window = 0, failed = 4))).isEqualTo(FeedState.ALL_FAILED)
        assertThat(FeedAssembler.chooseState(input(window = 0))).isEqualTo(FeedState.EMPTY_REGION)
        assertThat(FeedAssembler.chooseState(input(filtered = 0))).isEqualTo(FeedState.EMPTY_LENS)
        assertThat(FeedAssembler.chooseState(input(enabled = 0))).isEqualTo(FeedState.ALL_SOURCES_OFF)
        // Cached stories while offline stay Content (with the offline banner).
        assertThat(FeedAssembler.chooseState(input(online = false))).isEqualTo(FeedState.CONTENT)
    }

    @Test fun `banners - offline with cache, some sources failed, limited coverage`() {
        assertThat(FeedAssembler.banners(input(online = false), offlineSince = 9, limitedCoverage = false)).containsExactly(Banner.Offline(9))
        assertThat(FeedAssembler.banners(input(failed = 3), null, false)).containsExactly(Banner.SourcesFailed(3))
        assertThat(FeedAssembler.banners(input(failed = 4), null, false)).isEmpty() // all failed is its own state
        assertThat(FeedAssembler.banners(input(), null, true)).containsExactly(Banner.LimitedCoverage)
    }
}

class TrendingTest {
    @Test fun `results list is the sector's 7-day rows newest first`() {
        val now = 10L * 24 * 60 * 60 * 1000
        val ai = io.github.ukpratik.founderguru.core.tag.Sector.AI
        val rows = listOf(
            row(1, now - 1000).copy(sectors = ai.bit),
            row(2, now - 8L * 24 * 60 * 60 * 1000).copy(sectors = ai.bit),
            row(3, now - 10).copy(sectors = ai.bit),
            row(4, now - 5),
        )
        assertThat(Trending.results(rows, ai, now).map { it.id }).containsExactly(3L, 1L).inOrder()
    }
}
