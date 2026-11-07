// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.data

import android.util.Xml
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.fetch.FeedFetcher
import io.github.ukpratik.founderguru.core.fetch.HttpPolicy
import io.github.ukpratik.founderguru.core.model.FailureKind
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.SourceDef
import io.github.ukpratik.founderguru.core.model.SourcesCatalog
import io.github.ukpratik.founderguru.data.db.FgDatabase
import io.github.ukpratik.founderguru.data.db.StoryEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** The refresh pipeline end to end on in-memory Room + the platform XML parser + an HTTPS MockWebServer (LLD §4.1, §8). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RefreshRepositoryTest {
    private val now = Instant.parse("2026-11-07T12:00:00Z").toEpochMilli()
    private val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
    private val server = MockWebServer()
    private val bodies = ConcurrentHashMap<String, MockResponse>()
    private val hits = ConcurrentHashMap<String, AtomicInteger>()
    private lateinit var db: FgDatabase
    private lateinit var fetcher: FeedFetcher

    @Before fun setUp() {
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory())
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                hits.getOrPut(path) { AtomicInteger() }.incrementAndGet()
                if (request.headers["If-None-Match"] == "\"v1\"") return MockResponse.Builder().code(304).build()
                return bodies[path] ?: MockResponse.Builder().code(404).build()
            }
        }
        server.start()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val policy = HttpPolicy(HttpPolicy.userAgent("test"), callTimeoutMs = 5_000)
        val client = policy.newClient().newBuilder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
        fetcher = FeedFetcher(client, { Xml.newPullParser() }, policy)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FgDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() {
        db.close()
        server.close()
    }

    private fun src(id: String, region: Region, cap: Int? = null) =
        SourceDef(id, id.uppercase(), region, server.url("/$id").toString(), "https://example.com", dailyCap = cap)

    private fun item(title: String, link: String, at: String = "2026-11-07T10:00:00Z") =
        "<item><title>$title</title><link>$link</link><pubDate>${DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.parse(at).atOffset(ZoneOffset.UTC))}</pubDate><description>About $title</description></item>"

    private fun serve(id: String, vararg items: String, etag: String? = null) {
        val b = MockResponse.Builder().code(200).body("<?xml version=\"1.0\"?><rss><channel>${items.joinToString("")}</channel></rss>")
        etag?.let { b.addHeader("ETag", it) }
        bodies["/$id"] = b.build()
    }

    private fun repo(vararg sources: SourceDef): RefreshRepository {
        val catalog = SourcesCatalog(1, 1, sources.toList())
        runBlocking { SourcesRepository(db).sync(catalog) }
        return RefreshRepository(db, fetcher, catalog, clock = { now }, zone = { ZoneOffset.UTC })
    }

    /** A repository that refreshes just [s], so tests control the fetch order. */
    private fun only(s: SourceDef) = RefreshRepository(db, fetcher, SourcesCatalog(1, 1, listOf(s)), { now }, { ZoneOffset.UTC })

    private fun stories(): List<StoryEntity> = runBlocking { listOf("in-a", "in-b", "gl-a", "gl-b").flatMap { db.stories().bySource(it) } }

    @Test fun `refresh stores stories and source health, a second run is 304 with no duplicates`() = runBlocking<Unit> {
        serve("in-a", item("Zepto raises big new round", "https://news.in/zepto?utm_source=rss"), etag = "\"v1\"")
        val repo = repo(src("in-a", Region.IN))
        val first = repo.refresh(Region.IN)
        assertThat(first.updated).containsExactly("in-a")
        val story = db.stories().bySource("in-a").single()
        assertThat(story.url).isEqualTo("https://news.in/zepto")
        assertThat(story.urlKey).isEqualTo("news.in/zepto")
        assertThat(story.firstSeenAt).isEqualTo(now)
        val state = db.sources().byId("in-a")!!
        assertThat(state.lastStatus).isEqualTo("OK")
        assertThat(state.etag).isEqualTo("\"v1\"")
        assertThat(state.newestItemAt).isEqualTo(Instant.parse("2026-11-07T10:00:00Z").toEpochMilli())

        val second = repo.refresh(Region.IN)
        assertThat(second.notModified).containsExactly("in-a")
        assertThat(db.stories().count()).isEqualTo(1)
        assertThat(db.sources().byId("in-a")!!.lastStatus).isEqualTo("NOT_MODIFIED")
    }

    @Test fun `one failing source never breaks the others, and its cached stories stay (NFR-003)`() = runBlocking<Unit> {
        serve("in-a", item("Story one from source a", "https://a.in/1"))
        serve("in-b", item("Story one from source b", "https://b.in/1"))
        val repo = repo(src("in-a", Region.IN), src("in-b", Region.IN))
        repo.refresh(Region.IN)
        bodies["/in-b"] = MockResponse.Builder().code(500).build()
        bodies["/in-a"] = MockResponse.Builder().code(200).body("<rss><channel><item><title>broken</bad>").build()
        val result = repo.refresh(Region.IN)
        assertThat(result.failed).containsExactly("in-a", FailureKind.PARSE, "in-b", FailureKind.HTTP)
        assertThat(db.stories().count()).isEqualTo(2)
        val b = db.sources().byId("in-b")!!
        assertThat(b.lastStatus).isEqualTo("HTTP")
        assertThat(b.lastHttpCode).isEqualTo(500)
        assertThat(b.consecutiveFailures).isEqualTo(1)
        assertThat(b.lastSuccessAt).isEqualTo(now)
    }

    @Test fun `region filter - only the region and GLOBAL, only enabled sources`() = runBlocking<Unit> {
        serve("in-a", item("India story number one here", "https://a.in/1"))
        serve("gl-a", item("Global story number one here", "https://g.com/1"))
        bodies["/eu-a"] = MockResponse.Builder().code(200).body("<rss/>").build()
        val repo = repo(src("in-a", Region.IN), src("gl-a", Region.GLOBAL), src("eu-a", Region.EU))
        db.sources().setEnabled("gl-a", false)
        val r = repo.refresh(Region.IN)
        assertThat(r.updated).containsExactly("in-a")
        assertThat(hits.keys).containsExactly("/in-a")
    }

    @Test fun `same URL from two sources is stored once`() = runBlocking<Unit> {
        serve("gl-a", item("Shared story about a big raise", "https://www.x.com/s/?utm_medium=rss"))
        serve("in-a", item("Different title same link here", "https://x.com/s"))
        repo(src("gl-a", Region.GLOBAL), src("in-a", Region.IN)).refresh(Region.IN)
        assertThat(db.stories().count()).isEqualTo(1)
    }

    @Test fun `title de-dup - the regional copy wins whatever the fetch order (D-023 G-2)`() = runBlocking<Unit> {
        val title = "Zepto raises 450 million in Series G"
        // Global first, then regional: the regional copy replaces the Global one.
        serve("gl-a", item(title, "https://global.com/zepto", "2026-11-07T08:00:00Z"))
        serve("in-a", item(title, "https://india.in/zepto", "2026-11-06T09:00:00Z"))
        repo(src("gl-a", Region.GLOBAL), src("in-a", Region.IN))
        only(src("gl-a", Region.GLOBAL)).refresh(null)
        assertThat(stories().map { it.sourceId }).containsExactly("gl-a")
        only(src("in-a", Region.IN)).refresh(null)
        assertThat(stories().map { it.sourceId }).containsExactly("in-a")
    }

    @Test fun `title de-dup - regional first, then Global is skipped (D-023 G-2)`() = runBlocking<Unit> {
        val title = "Zepto raises 450 million in Series G"
        serve("in-a", item(title, "https://india.in/zepto"))
        serve("gl-a", item(title, "https://global.com/zepto"))
        repo(src("in-a", Region.IN), src("gl-a", Region.GLOBAL))
        only(src("in-a", Region.IN)).refresh(null)
        only(src("gl-a", Region.GLOBAL)).refresh(null)
        assertThat(stories().map { it.sourceId }).containsExactly("in-a")
    }

    @Test fun `title de-dup - same scope keeps the earlier first-seen copy, a saved Global copy is never replaced`() = runBlocking<Unit> {
        val title = "Zepto raises 450 million in Series G"
        serve("in-a", item(title, "https://a.in/zepto"))
        serve("in-b", item(title, "https://b.in/zepto"))
        serve("gl-a", item(title, "https://g.com/zepto"))
        serve("gl-b", item("Other company launches a new thing", "https://g.com/other"))
        repo(src("in-a", Region.IN), src("in-b", Region.IN), src("gl-a", Region.GLOBAL), src("gl-b", Region.GLOBAL))
        only(src("gl-a", Region.GLOBAL)).refresh(null)
        val global = db.stories().bySource("gl-a").single()
        db.stories().setSavedAt(global.id, now)
        only(src("in-a", Region.IN)).refresh(null)
        only(src("in-b", Region.IN)).refresh(null)
        assertThat(stories().map { it.sourceId }).containsExactly("gl-a")

        // Without the saved Global copy, in-a wins and in-b (same scope, later) is skipped.
        db.clearAllTables()
        repo(src("in-a", Region.IN), src("in-b", Region.IN))
        only(src("in-a", Region.IN)).refresh(null)
        only(src("in-b", Region.IN)).refresh(null)
        assertThat(stories().map { it.sourceId }).containsExactly("in-a")
    }

    @Test fun `short generic titles are not de-duplicated`() = runBlocking<Unit> {
        serve("in-a", item("Weekly funding roundup", "https://a.in/w"))
        serve("in-b", item("Weekly funding roundup", "https://b.in/w"))
        repo(src("in-a", Region.IN), src("in-b", Region.IN)).refresh(Region.IN)
        assertThat(db.stories().count()).isEqualTo(2)
    }

    @Test fun `stored stories carry lens, sector and region tags (M2)`() = runBlocking<Unit> {
        serve("gl-a", item("Bengaluru fintech startup raises seed round", "https://g.com/1"))
        serve("in-a", item("Bengaluru fintech startup raises seed round again", "https://a.in/1"))
        val global = src("gl-a", Region.GLOBAL).copy(defaultLens = io.github.ukpratik.founderguru.core.model.Lens.TRENDS)
        repo(global, src("in-a", Region.IN))
        only(global).refresh(null)
        only(src("in-a", Region.IN)).refresh(null)
        val g = db.stories().bySource("gl-a").single()
        assertThat(g.lenses).isEqualTo(io.github.ukpratik.founderguru.core.model.Lens.FUNDING.bit or io.github.ukpratik.founderguru.core.model.Lens.TRENDS.bit)
        assertThat(g.sectors and io.github.ukpratik.founderguru.core.tag.Sector.FINTECH.bit).isNotEqualTo(0L)
        assertThat(g.regionMentions).isEqualTo(Region.IN.mentionBit)
        assertThat(db.stories().bySource("in-a").single().regionMentions).isEqualTo(0) // regional sources: never set
    }

    @Test fun `keyword-filtered sources keep only startup stories (M2)`() = runBlocking<Unit> {
        serve("in-a", item("Peppermint Robotics raises Rs 100 crore", "https://a.in/1"), item("Lenskart Q2 net profit more than doubles", "https://a.in/2"))
        val filtered = src("in-a", Region.IN).copy(needsKeywordFilter = true)
        repo(filtered)
        only(filtered).refresh(null)
        assertThat(db.stories().bySource("in-a").map { it.url }).containsExactly("https://a.in/1")
        assertThat(db.sources().byId("in-a")!!.lastStatus).isEqualTo("OK")
    }

    @Test fun `concurrent callers share one run (no double fetch)`() = runBlocking<Unit> {
        serve("in-a", item("Zepto raises big new round", "https://news.in/zepto"))
        val repo = repo(src("in-a", Region.IN))
        val a = async { repo.refresh(Region.IN) }
        val b = async { repo.refresh(Region.IN) }
        assertThat(a.await()).isEqualTo(b.await())
        assertThat(hits["/in-a"]!!.get()).isEqualTo(1)
    }

    @Test fun `daily cap counts stored items per day`() = runBlocking<Unit> {
        serve("gl-a", *(1..7).map { item("Launch number $it of the day", "https://ph.com/$it") }.toTypedArray())
        val repo = repo(src("gl-a", Region.GLOBAL, cap = 5))
        repo.refresh(null)
        assertThat(db.stories().count()).isEqualTo(5)
        repo.refresh(null)
        assertThat(db.stories().count()).isEqualTo(5)
    }

    @Test fun `prune - 14 days and 2,000 unsaved, saved stories are unlimited and never pruned (D-023 G-5)`() = runBlocking<Unit> {
        repo(src("in-a", Region.IN))
        val day = 86_400_000L
        fun s(i: Int, at: Long, saved: Boolean = false) = StoryEntity(
            sourceId = "in-a", sourceName = "A", url = "https://a/$i", urlKey = "a/$i", title = "t$i", titleKey = "t$i",
            snippet = "", imageUrl = null, publishedAt = at, firstSeenAt = at, savedAt = if (saved) at else null,
        )
        db.stories().insert(s(-1, now - 15 * day))
        db.stories().insert(s(-2, now - 30 * day, saved = true))
        for (i in 0 until 2_050) db.stories().insert(s(i, now - i * 1_000L))
        for (i in 0 until 10) db.stories().insert(s(10_000 + i, now - 20_000_000L - i, saved = true))
        RefreshRepository(db, fetcher, SourcesCatalog(1, 1, emptyList()), { now }).prune(now)
        assertThat(db.stories().count() - db.stories().savedCount()).isEqualTo(2_000)
        assertThat(db.stories().savedCount()).isEqualTo(11)
        val keys = db.stories().bySource("in-a").map { it.urlKey }.toSet()
        assertThat(keys).contains("a/-2")
        assertThat(keys).doesNotContain("a/-1")
        assertThat(keys).contains("a/0")
        assertThat(keys).doesNotContain("a/2049")
    }
}
