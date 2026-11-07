// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core

import io.github.ukpratik.founderguru.core.parse.CharsetSniffer
import io.github.ukpratik.founderguru.core.parse.CleaningReader
import io.github.ukpratik.founderguru.core.parse.FeedParser
import io.github.ukpratik.founderguru.core.parse.ParseResult
import org.kxml2.io.KXmlParser
import java.io.ByteArrayInputStream
import java.io.File

/** Shared helpers: fixture access and the clean + parse path exactly as the fetcher runs it. */
object TestFeeds {
    val parserFactory = { KXmlParser() }

    fun bytes(name: String): ByteArray =
        requireNotNull(TestFeeds::class.java.getResourceAsStream("/feeds/$name")) { "missing fixture $name" }.use { it.readBytes() }

    fun parse(bytes: ByteArray, maxItems: Int = FeedParser.MAX_ITEMS): ParseResult =
        FeedParser(parserFactory, maxItems).parse(CleaningReader(CharsetSniffer.reader(ByteArrayInputStream(bytes), null)))

    fun parse(name: String): ParseResult = parse(bytes(name))

    fun feed(name: String): ParseResult.Feed = parse(name) as? ParseResult.Feed ?: error("$name did not parse: ${parse(name)}")

    /** The 24 captured real feeds (2026-11-07, trimmed to ≤ 10 items), by source id → fixture file. */
    val realFixtures: Map<String, String> = mapOf(
        "techcrunch-startups" to "techcrunch-startups.xml",
        "crunchbase-news" to "crunchbase-news.xml",
        "hn-show" to "hn-show.xml",
        "producthunt" to "atom-producthunt.xml",
        "yc-blog" to "yc-blog.xml",
        "first-round-review" to "first-round-review.xml",
        "rest-of-world" to "restofworld-unescaped-amp.xml",
        "inc42" to "inc42.xml",
        "yourstory" to "yourstory.xml",
        "entrackr" to "entrackr.xml",
        "ettech-startups" to "ettech-startups.xml",
        "sifted" to "sifted.xml",
        "tech-eu" to "tech-eu.xml",
        "tech-funding-news" to "tech-funding-news.xml",
        "geekwire-startups" to "geekwire-startups.xml",
        "betakit" to "betakit.xml",
        "alleywatch" to "alleywatch.xml",
        "vulcan-post" to "vulcan-post.xml",
        "fintech-news-sg" to "fintech-news-sg.xml",
        "techcabal" to "techcabal.xml",
        "disrupt-africa" to "disrupt-africa.xml",
        "weetracker" to "weetracker.xml",
        "latamlist" to "latamlist.xml",
        "lavca" to "lavca.xml",
    )

    fun shippedSourcesJson(): String = File(requireNotNull(System.getProperty("fg.sourcesJson"))).readText()
}
