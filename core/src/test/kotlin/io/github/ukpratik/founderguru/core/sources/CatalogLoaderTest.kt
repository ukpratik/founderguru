// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.sources

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.TestFeeds
import io.github.ukpratik.founderguru.core.model.Lens
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.SourceDef
import io.github.ukpratik.founderguru.core.model.SourcesCatalog
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogLoaderTest {
    private val shipped = CatalogLoader.load(TestFeeds.shippedSourcesJson(), strictKeys = true)

    @Test fun `shipped sources json is valid - 24 sources, 7 global`() {
        assertThat(shipped.sources).hasSize(24)
        assertThat(shipped.sources.count { it.region == Region.GLOBAL }).isEqualTo(7)
        assertThat(shipped.sources.map { it.id }).containsNoDuplicates()
    }

    @Test fun `shipped list matches LLD section 2_3`() {
        val byId = shipped.sources.associateBy { it.id }
        assertThat(byId.keys).containsExactlyElementsIn(TestFeeds.realFixtures.keys)
        assertThat(byId.getValue("producthunt").dailyCap).isEqualTo(5)
        assertThat(byId.getValue("crunchbase-news").defaultLens).isEqualTo(Lens.FUNDING)
        assertThat(shipped.sources.filter { it.needsKeywordFilter }.map { it.id })
            .containsExactly("yourstory", "ettech-startups", "vulcan-post", "techcabal") // D-025: YourStory added in M5
        assertThat(byId.getValue("disrupt-africa").url).isEqualTo("https://disruptafrica.com/feed/")
        assertThat(byId.getValue("yc-blog").staleAfterDays).isEqualTo(45)
        assertThat(byId.getValue("first-round-review").staleAfterDays).isEqualTo(60)
        assertThat(byId.getValue("lavca").staleAfterDays).isEqualTo(21)
        assertThat(shipped.sources.none { "paulgraham" in it.url || "aaronsw" in it.url }).isTrue()
    }

    @Test fun `rejects http, bad ids, duplicates and caps`() {
        val good = shipped.sources
        fun errors(sources: List<SourceDef>) = CatalogLoader.validate(SourcesCatalog(1, 1, sources))
        assertThat(errors(good)).isEmpty()
        assertThat(errors(good.mapIndexed { i, s -> if (i == 0) s.copy(url = "http://x.com/feed") else s }).single()).contains("https")
        assertThat(errors(good.mapIndexed { i, s -> if (i == 0) s.copy(id = "Bad_ID") else s }).single()).contains("id must match")
        assertThat(errors(good + good[0])).isNotEmpty()
        assertThat(errors(good.mapIndexed { i, s -> if (i == 0) s.copy(dailyCap = 0) else s }).single()).contains("dailyCap")
    }

    @Test fun `rejects a region with fewer than 2 sources and wrong totals`() {
        val withoutOneLatam = shipped.sources.filterNot { it.id == "lavca" }
        val errors = CatalogLoader.validate(SourcesCatalog(1, 1, withoutOneLatam))
        assertThat(errors).contains("region LATAM needs at least 2 sources, has 1")
        assertThat(errors).contains("expected 24 sources, found 23")
    }

    @Test fun `unknown keys - strict in tests, ignored at runtime`() {
        val json = TestFeeds.shippedSourcesJson().replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"extra\": true")
        assertThrows(InvalidCatalogException::class.java) { CatalogLoader.load(json, strictKeys = true) }
        assertThat(CatalogLoader.load(json).sources).hasSize(24)
    }

    @Test fun `unknown enum value is an invalid catalogue`() {
        val json = TestFeeds.shippedSourcesJson().replaceFirst("\"region\": \"IN\"", "\"region\": \"MARS\"")
        assertThrows(InvalidCatalogException::class.java) { CatalogLoader.load(json) }
    }
}
