// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.tools.feedcheck

import io.github.ukpratik.founderguru.core.fetch.FeedFetcher
import io.github.ukpratik.founderguru.core.fetch.HttpPolicy
import io.github.ukpratik.founderguru.core.model.HttpState
import io.github.ukpratik.founderguru.core.model.SourceDef
import io.github.ukpratik.founderguru.core.sources.CatalogLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import org.kxml2.io.KXmlParser
import java.io.File
import kotlin.system.exitProcess

/**
 * `./gradlew :tools:feedcheck:run --args="app/src/main/assets/sources.json build/feedcheck.json [--attempts 3] [--retry-delay-s 300]"`
 *
 * Fetches every source with the app's own fetcher and parser (same User-Agent, no conditional headers), retries the
 * broken ones up to `--attempts` times, and writes a JSON array of [SourceHealth] plus a Markdown table next to it.
 * Exit code 0 when the run completed (broken sources are data, the workflow turns them into issues); 2 when more than
 * half of the sources failed at the network level, which means the runner's network is the problem, not the feeds.
 */
fun main(args: Array<String>) {
    val positional = args.filterIndexed { i, a -> !a.startsWith("--") && (i == 0 || !args[i - 1].startsWith("--")) }
    fun opt(name: String, default: Int) = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1)?.toIntOrNull() } ?: default
    if (positional.size != 2) {
        System.err.println("usage: feedcheck <sources.json> <out.json> [--attempts N] [--retry-delay-s S]")
        exitProcess(64)
    }
    val (sourcesPath, outPath) = positional
    val attempts = opt("--attempts", 3).coerceIn(1, 5)
    val retryDelayMs = opt("--retry-delay-s", 300).coerceIn(0, 900) * 1000L

    val catalog = CatalogLoader.load(File(sourcesPath).readText())
    val policy = HttpPolicy(HttpPolicy.userAgent("feedcheck"))
    val fetcher = FeedFetcher(policy.newClient(), { KXmlParser() }, policy)

    val results = runBlocking {
        val byId = LinkedHashMap<String, SourceHealth>()
        var pending: List<SourceDef> = catalog.sources
        for (attempt in 1..attempts) {
            if (attempt > 1) {
                println("Retrying ${pending.size} broken source(s) in ${retryDelayMs / 1000} s: ${pending.joinToString { it.id }}")
                delay(retryDelayMs)
            }
            val permits = Semaphore(6)
            val round = pending.map { s ->
                async { permits.withPermit { FeedHealth.classify(s, fetcher.fetch(s, HttpState()), System.currentTimeMillis(), attempt) } }
            }.awaitAll()
            round.forEach { byId[it.id] = it }
            pending = catalog.sources.filter { byId[it.id]?.status == SourceHealth.Status.BROKEN }
            if (pending.isEmpty()) break
        }
        catalog.sources.map { byId.getValue(it.id) }
    }

    val out = File(outPath)
    out.absoluteFile.parentFile?.mkdirs()
    out.writeText(Json { prettyPrint = true; encodeDefaults = true }.encodeToString(results))
    val md = FeedHealth.markdown(results)
    File(out.path.removeSuffix(".json") + ".md").writeText(md)
    println(md)

    val networkFailures = results.count { it.status == SourceHealth.Status.BROKEN && (it.reason.startsWith("NETWORK") || it.reason.startsWith("TIMEOUT")) }
    if (networkFailures * 2 > results.size) {
        System.err.println("Inconclusive: $networkFailures of ${results.size} sources failed at the network level; not reporting feeds as broken.")
        exitProcess(2)
    }
}
