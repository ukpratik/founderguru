// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.feed

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.feed.Banner
import io.github.ukpratik.founderguru.core.feed.CaughtUpMarker
import io.github.ukpratik.founderguru.core.feed.LensFilter
import io.github.ukpratik.founderguru.core.model.Lens
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.tag.Sector
import io.github.ukpratik.founderguru.data.Tab
import io.github.ukpratik.founderguru.data.db.SourceStateEntity
import io.github.ukpratik.founderguru.data.prefs.Settings
import io.github.ukpratik.founderguru.ui.HOUR
import io.github.ukpratik.founderguru.ui.NOW
import io.github.ukpratik.founderguru.ui.indiaAndGlobal
import io.github.ukpratik.founderguru.ui.source
import io.github.ukpratik.founderguru.ui.story
import org.junit.Test

/** Every US-011 row and banner rule as a pure mapping (LLD §5, §8 "ViewModels"). */
class FeedStateMapperTest {
    private val india = Settings(onboardingDone = true, region = Region.IN, lastRefreshAt = NOW - HOUR)

    private fun map(
        settings: Settings = india,
        rows: List<io.github.ukpratik.founderguru.core.model.StoryRow> = listOf(story(1), story(2)),
        sources: List<SourceStateEntity> = indiaAndGlobal(),
        online: Boolean = true,
        refreshing: Boolean = false,
        anchor: Long? = null,
        tab: Tab = Tab.LOCAL,
    ) = FeedStateMapper.map(FeedInputs(tab, settings, rows, sources, online, refreshing, anchor, NOW))

    @Test fun `first sync shows the skeleton with the number of sources being fetched`() {
        val s = map(settings = india.copy(lastRefreshAt = null), rows = emptyList(), sources = indiaAndGlobal().map { it.copy(lastStatus = null, lastAttemptAt = null) })
        assertThat(s).isInstanceOf(FeedUiState.FirstSync::class.java)
        assertThat((s as FeedUiState.FirstSync).dateline).isEqualTo(DatelineStatus.Getting(11))
    }

    @Test fun `first launch with no network`() {
        val s = map(settings = india.copy(lastRefreshAt = null), rows = emptyList(), online = false, sources = indiaAndGlobal().map { it.copy(lastAttemptAt = null) })
        assertThat(s).isInstanceOf(FeedUiState.FirstSyncOffline::class.java)
    }

    @Test fun `later with no network keeps cached stories and adds the offline strip`() {
        val s = map(online = false) as FeedUiState.Content
        assertThat(s.banners).containsExactly(Banner.Offline(NOW - HOUR))
        assertThat(s.dateline).isEqualTo(DatelineStatus.Updated(NOW - HOUR, offline = true))
    }

    @Test fun `some sources failed shows the note until dismissed after that refresh`() {
        val sources = indiaAndGlobal().mapIndexed { i, s -> if (i < 3) s.copy(lastStatus = "TIMEOUT") else s }
        val shown = map(sources = sources) as FeedUiState.Content
        assertThat(shown.banners).containsExactly(Banner.SourcesFailed(3))
        val dismissed = map(settings = india.copy(failedNoteDismissedAt = NOW - HOUR / 2), sources = sources) as FeedUiState.Content
        assertThat(dismissed.banners).isEmpty()
        // A later refresh with failures brings it back.
        val later = map(settings = india.copy(failedNoteDismissedAt = NOW - HOUR / 2), sources = sources.map { it.copy(lastAttemptAt = NOW) }) as FeedUiState.Content
        assertThat(later.banners).containsExactly(Banner.SourcesFailed(3))
    }

    @Test fun `all region sources failed and nothing cached`() {
        val sources = indiaAndGlobal().map { if (it.region == "IN") it.copy(lastStatus = "NETWORK") else it }
        val s = map(rows = emptyList(), sources = sources)
        assertThat(s).isEqualTo(FeedUiState.AllFailed(FeedHeader(Tab.LOCAL, Region.IN), 4))
        assertThat((s as FeedUiState.AllFailed).canSeeGlobal).isTrue()
        // Retrying shows the skeleton again.
        assertThat(map(rows = emptyList(), sources = sources, refreshing = true)).isInstanceOf(FeedUiState.FirstSync::class.java)
    }

    @Test fun `empty lens, empty region and all sources off`() {
        val lens = map(settings = india.copy(lensLocal = LensFilter.Only(Lens.PLAYBOOKS))) as FeedUiState.Empty
        assertThat(lens.reason).isEqualTo(EmptyReason.LENS_EMPTY)
        val region = map(rows = emptyList()) as FeedUiState.Empty
        assertThat(region.reason).isEqualTo(EmptyReason.NO_STORIES_IN_REGION)
        assertThat(region.sourceCount).isEqualTo(4)
        val off = map(rows = emptyList(), sources = indiaAndGlobal().map { if (it.region == "IN") it.copy(enabled = false) else it }) as FeedUiState.Empty
        assertThat(off.reason).isEqualTo(EmptyReason.ALL_SOURCES_OFF)
    }

    @Test fun `limited coverage banner shows on Local for SEA and LatAm until dismissed (G-10)`() {
        val sea = Settings(onboardingDone = true, region = Region.SEA, lastRefreshAt = NOW - HOUR)
        val sources = listOf(source("vp", Region.SEA)) + (1..7).map { source("gl$it", Region.GLOBAL) }
        val local = map(settings = sea, sources = sources) as FeedUiState.Content
        assertThat(local.banners).containsExactly(Banner.LimitedCoverage)
        assertThat((map(settings = sea, sources = sources, tab = Tab.GLOBAL) as FeedUiState.Content).banners).isEmpty()
        val dismissed = map(settings = sea.copy(limitedBannerDismissed = setOf(Region.SEA)), sources = sources) as FeedUiState.Content
        assertThat(dismissed.banners).isEmpty()
        assertThat((map(sources = sources) as FeedUiState.Empty).banners).isEmpty() // India never shows it
    }

    @Test fun `banner order is offline, limited coverage, sources failed`() {
        val sea = Settings(onboardingDone = true, region = Region.SEA, lastRefreshAt = NOW - HOUR)
        val sources = listOf(source("vp", Region.SEA), source("dsx", Region.SEA, status = "BLOCKED")) + (1..7).map { source("gl$it", Region.GLOBAL) }
        val s = map(settings = sea, sources = sources, online = false) as FeedUiState.Content
        assertThat(s.banners).containsExactly(Banner.Offline(NOW - HOUR), Banner.LimitedCoverage, Banner.SourcesFailed(1)).inOrder()
    }

    @Test fun `sections follow the frozen anchor and the selected lens`() {
        val rows = listOf(
            story(1, publishedAgo = HOUR, firstSeenAgo = HOUR, lenses = setOf(Lens.FUNDING)),
            story(2, publishedAgo = 2 * HOUR, firstSeenAgo = 2 * HOUR),
            story(3, publishedAgo = 30 * HOUR, firstSeenAgo = 30 * HOUR, lenses = setOf(Lens.FUNDING)),
        )
        val all = map(rows = rows, anchor = NOW - 3 * HOUR) as FeedUiState.Content
        assertThat(all.sections.marker).isEqualTo(CaughtUpMarker.AFTER_NEW)
        assertThat(all.sections.new.map { it.id }).containsExactly(1L, 2L).inOrder()
        assertThat(all.sections.earlier.map { it.id }).containsExactly(3L)
        val funding = map(settings = india.copy(lensLocal = LensFilter.Only(Lens.FUNDING)), rows = rows, anchor = NOW - 3 * HOUR) as FeedUiState.Content
        assertThat(funding.sections.newCount).isEqualTo(1)
        val first = map(rows = rows, anchor = null) as FeedUiState.Content
        assertThat(first.sections.marker).isEqualTo(CaughtUpMarker.END_OF_LIST)
        val nothing = map(rows = rows, anchor = NOW) as FeedUiState.Content
        assertThat(nothing.sections.marker).isEqualTo(CaughtUpMarker.TOP_NOTHING_NEW)
    }

    @Test fun `what's hot needs three qualifying sectors`() {
        val sectors = listOf(Sector.FINTECH, Sector.AI, Sector.HEALTH)
        val rows = sectors.flatMapIndexed { i, sector -> (0 until 3).map { j -> story(i * 10L + j, source = "s$j", sectors = setOf(sector)) } }
        assertThat((map(rows = rows) as FeedUiState.Content).hot.map { it.sector }).containsExactlyElementsIn(sectors)
        assertThat((map(rows = rows.filter { it.sectors != Sector.HEALTH.bit }) as FeedUiState.Content).hot).isEmpty()
    }

    @Test fun `global only reads Global sources on the Global tab`() {
        val g = Settings(onboardingDone = true, region = Region.GLOBAL, lastRefreshAt = NOW - HOUR)
        val s = map(settings = g, tab = Tab.GLOBAL) as FeedUiState.Content
        assertThat(s.header.globalOnly).isTrue()
        assertThat(s.header.tabRegion).isEqualTo(Region.GLOBAL)
    }

    @Test fun `refreshing with cached stories keeps the content and says so in the dateline`() {
        val s = map(refreshing = true) as FeedUiState.Content
        assertThat(s.isRefreshing).isTrue()
        assertThat(s.dateline).isEqualTo(DatelineStatus.Refreshing)
    }
}
