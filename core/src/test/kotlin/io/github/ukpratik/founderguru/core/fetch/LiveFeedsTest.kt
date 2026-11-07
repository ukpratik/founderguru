// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.fetch

import io.github.ukpratik.founderguru.core.TestFeeds
import io.github.ukpratik.founderguru.core.model.FetchOutcome
import io.github.ukpratik.founderguru.core.model.HttpState
import io.github.ukpratik.founderguru.core.model.StoryDraft
import io.github.ukpratik.founderguru.core.normalise.ItemNormaliser
import io.github.ukpratik.founderguru.core.parse.FeedDates
import io.github.ukpratik.founderguru.core.sources.CatalogLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * M1 demo: the real 24 feeds through the app's own fetch + clean + parse + normalise path. Network; opt-in only:
 * `./gradlew :core:test --tests '*LiveFeedsTest' -Pfg.live=true`. Never part of CI (the weekly feed-health job is M5).
 * With `-Pfg.capture=<file>` it also writes every normalised item (ignoring the 14-day window) as the raw pool for
 * `lens-testset.json`.
 */
class LiveFeedsTest {
    @Test fun `live feeds report`() {
        assumeTrue("set -Pfg.live=true", System.getProperty("fg.live") == "true")
        val catalog = CatalogLoader.load(TestFeeds.shippedSourcesJson())
        val policy = HttpPolicy(HttpPolicy.userAgent("0.1.0"))
        val fetcher = FeedFetcher(policy.newClient(), TestFeeds.parserFactory, policy)
        val now = System.currentTimeMillis()
        val started = System.nanoTime()
        val permits = Semaphore(6)
        val pool = java.util.concurrent.ConcurrentHashMap<String, List<StoryDraft>>()
        val lines = runBlocking {
            catalog.sources.map { s ->
                async {
                    permits.withPermit {
                        when (val o = fetcher.fetch(s, HttpState())) {
                            is FetchOutcome.Updated -> {
                                val drafts = o.items.mapNotNull { ItemNormaliser.normalise(it, o.fetchedUrl, now) }
                                // Capture pool: no 14-day window (normalise "just after" each item).
                                pool[s.id] = o.items.mapNotNull { item ->
                                    val at = FeedDates.parse(item.published) ?: now
                                    ItemNormaliser.normalise(item, o.fetchedUrl, maxOf(at, 0) + 1)
                                }
                                "OK   %-22s items=%2d drafts=%2d images=%2d%s".format(
                                    s.id, o.items.size, drafts.size, drafts.count { it.imageUrl != null }, if (o.truncated) " truncated" else "",
                                )
                            }
                            FetchOutcome.NotModified -> "304  ${s.id}"
                            is FetchOutcome.Failed -> "FAIL %-22s %s %s %s".format(s.id, o.kind, o.httpCode ?: "", o.message)
                        }
                    }
                }
            }.awaitAll()
        }
        lines.forEach(::println)
        println("Total: ${lines.count { it.startsWith("OK") }}/${lines.size} OK in ${(System.nanoTime() - started) / 1_000_000} ms")
        System.getProperty("fg.capture")?.let { path ->
            val items = catalog.sources.flatMap { s ->
                pool[s.id].orEmpty().mapIndexed { i, d ->
                    buildJsonObject {
                        put("id", JsonPrimitive("${s.id}-${i + 1}"))
                        put("sourceId", JsonPrimitive(s.id))
                        put("title", JsonPrimitive(d.title))
                        put("snippet", JsonPrimitive(d.snippet))
                    }
                }
            }
            File(path).writeText(Json { prettyPrint = true }.encodeToString(JsonArray.serializer(), JsonArray(items)))
            println("Captured ${items.size} items to $path")
        }
    }
}
