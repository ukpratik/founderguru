// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.hot

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.tag.Sector
import io.github.ukpratik.founderguru.data.Tab
import io.github.ukpratik.founderguru.ui.HOUR
import io.github.ukpratik.founderguru.ui.NOW
import io.github.ukpratik.founderguru.ui.feed.FeedHeader
import io.github.ukpratik.founderguru.ui.story
import org.junit.Test

/** What's hot results (US-006, SCR-05 states 01–03). */
class HotStateMapperTest {
    private val header = FeedHeader(Tab.LOCAL, Region.IN)
    private val rows = listOf(Sector.FINTECH to 4, Sector.AI to 3, Sector.HEALTH to 3).flatMapIndexed { i, (sector, n) ->
        (0 until n).map { j -> story(i * 10L + j, source = "s${j % 2}", publishedAgo = (j + 1) * HOUR, sectors = setOf(sector)) }
    }

    @Test fun `rank, count and the sector's stories newest first`() {
        val s = HotStateMapper.map(header, Sector.AI, rows, online = true, lastRefreshAt = NOW, now = NOW) as HotUiState.Content
        assertThat(s.rank).isEqualTo(2)
        assertThat(s.count).isEqualTo(3)
        assertThat(s.rows.map { it.id }).containsExactly(10L, 11L, 12L).inOrder()
        assertThat(s.offline).isFalse()
    }

    @Test fun `a sector that no longer qualifies`() {
        val s = HotStateMapper.map(header, Sector.SPACE, rows, online = true, lastRefreshAt = NOW, now = NOW)
        assertThat(s).isEqualTo(HotUiState.NotQualifying(header, Sector.SPACE, null, false))
    }

    @Test fun `offline adds the strip time`() {
        val s = HotStateMapper.map(header, Sector.FINTECH, rows, online = false, lastRefreshAt = NOW - HOUR, now = NOW) as HotUiState.Content
        assertThat(s.offline).isTrue()
        assertThat(s.offlineSince).isEqualTo(NOW - HOUR)
        assertThat(s.rank).isEqualTo(1)
    }
}
