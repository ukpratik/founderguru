// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.parse

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.TestFeeds
import io.github.ukpratik.founderguru.core.normalise.ItemNormaliser
import org.junit.Test
import kotlin.random.Random

/**
 * NFR-003 (LLD §8): 1,000 seeded byte mutations of 5 fixtures through clean + parse + normalise. No exception may
 * escape; every outcome must be one of the typed [ParseResult]s.
 */
class RobustnessFuzzTest {
    private val fixtures = listOf(
        "inc42.xml", "atom-producthunt.xml", "restofworld-unescaped-amp.xml", "condia-undeclared-media-ns.xml", "hn-show.xml",
    ).map { TestFeeds.bytes(it) }

    private val inserts = listOf("&", "<", ">", "\u0000", "]]>", "<![CDATA[", "&#x0;", "\uD800").map { it.toByteArray() } +
        listOf(byteArrayOf(0xC3.toByte()), byteArrayOf(0xFF.toByte(), 0xFE.toByte()), byteArrayOf(0x80.toByte()))

    private fun mutate(src: ByteArray, rnd: Random): ByteArray {
        var b = src
        repeat(1 + rnd.nextInt(4)) {
            b = when (rnd.nextInt(4)) {
                0 -> b.copyOf(rnd.nextInt(b.size + 1)) // truncation
                1 -> {
                    val at = rnd.nextInt(b.size + 1)
                    b.copyOfRange(0, at) + inserts[rnd.nextInt(inserts.size)] + b.copyOfRange(at, b.size)
                }
                2 -> if (b.isEmpty()) b else b.copyOf().also { it[rnd.nextInt(it.size)] = rnd.nextInt(256).toByte() }
                else -> {
                    if (b.size < 2) b else {
                        val from = rnd.nextInt(b.size)
                        val len = rnd.nextInt(minOf(200, b.size - from) + 1)
                        b.copyOfRange(0, from) + b.copyOfRange(from + len, b.size)
                    }
                }
            }
        }
        return b
    }

    @Test fun `1000 seeded mutations never escape as exceptions`() {
        val rnd = Random(20261107)
        val now = FeedDates.parse("2026-11-08T00:00:00Z")!!
        var feeds = 0
        repeat(1000) { i ->
            val bytes = mutate(fixtures[i % fixtures.size], rnd)
            when (val r = TestFeeds.parse(bytes)) {
                is ParseResult.Feed -> {
                    feeds++
                    r.items.forEach { ItemNormaliser.normalise(it, "https://example.org/feed", now) }
                }
                ParseResult.NotAFeed, ParseResult.TooLarge, is ParseResult.Failed -> Unit
            }
        }
        // Most mutations should still yield items (the cleaner and partial-feed handling at work).
        assertThat(feeds).isAtLeast(500)
    }
}
