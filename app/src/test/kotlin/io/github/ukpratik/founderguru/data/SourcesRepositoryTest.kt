// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.AppContainer
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.SourceDef
import io.github.ukpratik.founderguru.core.model.SourcesCatalog
import io.github.ukpratik.founderguru.core.sources.CatalogLoader
import io.github.ukpratik.founderguru.data.db.FgDatabase
import io.github.ukpratik.founderguru.data.db.StoryEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Catalogue sync (LLD §2.4). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SourcesRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FgDatabase::class.java)
        .allowMainThreadQueries().build()
    private val repo = SourcesRepository(db)

    @After fun tearDown() = db.close()

    private fun src(id: String, url: String = "https://$id.com/feed") = SourceDef(id, id, Region.IN, url, "https://$id.com")

    private fun story(id: Int, sourceId: String, saved: Boolean) = StoryEntity(
        sourceId = sourceId, sourceName = sourceId, url = "https://x/$id", urlKey = "x/$id", title = "t", titleKey = "t",
        snippet = "", imageUrl = null, publishedAt = 1, firstSeenAt = 1, savedAt = if (saved) 1 else null,
    )

    @Test fun `the shipped asset loads and syncs 24 enabled sources`() = runBlocking<Unit> {
        val json = ApplicationProvider.getApplicationContext<android.content.Context>().assets
            .open(AppContainer.SOURCES_ASSET).bufferedReader().readText()
        repo.sync(CatalogLoader.load(json))
        val all = db.sources().all()
        assertThat(all).hasSize(24)
        assertThat(all.all { it.enabled }).isTrue()
    }

    @Test fun `sync keeps user and HTTP state, resets HTTP state when the URL changes`() = runBlocking<Unit> {
        repo.sync(SourcesCatalog(1, 1, listOf(src("a"), src("b"))))
        db.sources().upsert(db.sources().byId("a")!!.copy(enabled = false, etag = "e", lastModified = "m", finalUrl = "https://a2.com/feed"))
        db.sources().upsert(db.sources().byId("b")!!.copy(etag = "e"))
        repo.sync(SourcesCatalog(1, 1, listOf(src("a"), src("b", url = "https://b.com/new-feed"))))
        val a = db.sources().byId("a")!!
        assertThat(a.enabled).isFalse()
        assertThat(a.etag).isEqualTo("e")
        assertThat(a.finalUrl).isEqualTo("https://a2.com/feed")
        val b = db.sources().byId("b")!!
        assertThat(b.url).isEqualTo("https://b.com/new-feed")
        assertThat(b.etag).isNull()
    }

    @Test fun `removed sources lose their unsaved stories, saved stories survive`() = runBlocking<Unit> {
        repo.sync(SourcesCatalog(1, 1, listOf(src("a"), src("b"))))
        db.stories().insert(story(1, "b", saved = false))
        db.stories().insert(story(2, "b", saved = true))
        db.stories().insert(story(3, "a", saved = false))
        repo.sync(SourcesCatalog(1, 1, listOf(src("a"))))
        assertThat(db.sources().all().map { it.id }).containsExactly("a")
        assertThat(db.stories().bySource("b").map { it.urlKey }).containsExactly("x/2")
        assertThat(db.stories().bySource("a")).hasSize(1)
    }
}
