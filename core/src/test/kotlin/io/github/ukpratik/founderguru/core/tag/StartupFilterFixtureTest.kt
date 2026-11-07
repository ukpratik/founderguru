// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.tag

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.ukpratik.founderguru.core.TestFeeds
import io.github.ukpratik.founderguru.core.parse.FeedDates
import io.github.ukpratik.founderguru.core.normalise.ItemNormaliser
import io.github.ukpratik.founderguru.core.sources.CatalogLoader
import org.junit.Test

/**
 * D-025 (M5): the keyword filter on the real captured feeds, through the same parse + normalise + [StartupFilter] path
 * as `RefreshRepository`. The YourStory fixture (captured 2026-11-07) holds the art-festival photo essay that topped
 * Local India in the M4 smoke run.
 */
class StartupFilterFixtureTest {
    private fun keptAndDropped(fixture: String): Pair<List<String>, List<String>> {
        val items = TestFeeds.feed(fixture).items
        val now = items.mapNotNull { FeedDates.parse(it.published) }.max() + 86_400_000
        val drafts = items.mapNotNull { ItemNormaliser.normalise(it, "https://example.org/feed", now) }
        val (kept, dropped) = drafts.partition { StartupFilter.keep(WordMatcher.text(it.title, it.snippet)) }
        return kept.map { it.title } to dropped.map { it.title }
    }

    @Test fun `YourStory - startup stories pass, the lifestyle photo essay is dropped`() {
        val (kept, dropped) = keptAndDropped("yourstory.xml")
        assertThat(dropped.single { "art festival" in it }).startsWith("Vast, varied, vivid")
        val startup = listOf(
            "TDK Ventures",                    // investor interview
            "Weekly funding roundup",          // funding
            "India’s startup growth",          // ecosystem analysis
            "VeloDB",                          // founder interview
            "next wave of founders",           // founder programme
            "Aina launches",                   // launch
        )
        for (s in startup) assertWithMessage(s).that(kept.any { s in it }).isTrue()
        // Known, accepted drops (no startup wording in title or snippet): a court ruling on BharatPe's shareholder rights,
        // and a big-tech CEO's comments on AI security. 1 genuine startup story of 8 in this capture (D-025 threshold ~10%
        // is measured on the larger live sample in build-plan.md, M5 demo note).
        assertThat(dropped).hasSize(3)
    }

    @Test fun `every filtered source keeps most of its captured fixture`() {
        val catalog = CatalogLoader.load(TestFeeds.shippedSourcesJson())
        for (s in catalog.sources.filter { it.needsKeywordFilter }) {
            val (kept, dropped) = keptAndDropped(TestFeeds.realFixtures.getValue(s.id))
            assertWithMessage("${s.id} kept $kept dropped $dropped").that(kept.size).isAtLeast(dropped.size)
        }
    }
}
