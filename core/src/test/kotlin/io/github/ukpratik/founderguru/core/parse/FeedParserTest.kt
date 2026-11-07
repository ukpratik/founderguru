// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.parse

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.ukpratik.founderguru.core.TestFeeds
import io.github.ukpratik.founderguru.core.model.MediaRef
import io.github.ukpratik.founderguru.core.normalise.ItemNormaliser
import org.junit.Test

class FeedParserTest {

    @Test fun `every captured real feed parses with items, titles and links`() {
        for ((id, file) in TestFeeds.realFixtures) {
            val feed = TestFeeds.feed(file)
            assertWithMessage(id).that(feed.items.size).isAtLeast(5)
            assertWithMessage(id).that(feed.truncated).isFalse()
            for (item in feed.items) {
                assertWithMessage("$id title").that(item.title).isNotEmpty()
                assertWithMessage("$id link").that(item.link ?: item.guid).isNotEmpty()
                assertWithMessage("$id date").that(FeedDates.parse(item.published)).isNotNull()
            }
        }
    }

    @Test fun `every captured real feed normalises to drafts`() {
        for ((id, file) in TestFeeds.realFixtures) {
            val items = TestFeeds.feed(file).items
            // Low-volume feeds (First Round: ~2 a month) fall outside the 14-day window by design, so normalise each
            // fixture "a day after" its newest item.
            val now = items.mapNotNull { FeedDates.parse(it.published) }.max() + 86_400_000
            val drafts = items.mapNotNull { ItemNormaliser.normalise(it, "https://example.org/feed", now) }
            assertWithMessage(id).that(drafts).isNotEmpty()
            drafts.forEach {
                assertWithMessage(id).that(it.snippet.length).isAtMost(200)
                assertWithMessage(id).that(it.url).startsWith("http")
                it.imageUrl?.let { img -> assertWithMessage(id).that(img).startsWith("https://") }
            }
        }
    }

    @Test fun `Rest of World with unescaped ampersands parses after cleaning`() {
        val bytes = TestFeeds.bytes("restofworld-unescaped-amp.xml")
        val raw = FeedParser(TestFeeds.parserFactory).parse(bytes.inputStream().reader())
        assertThat(raw).isNotInstanceOf(ParseResult.Feed::class.java) // proves the fixture is really malformed
        assertThat(TestFeeds.feed("restofworld-unescaped-amp.xml").items).hasSize(10)
    }

    @Test fun `Condia with an undeclared media prefix parses and keeps media refs`() {
        val items = TestFeeds.feed("condia-undeclared-media-ns.xml").items
        assertThat(items).hasSize(10)
        assertThat(items.flatMap { it.media }.map { it.kind }).contains(MediaRef.Kind.MEDIA_CONTENT)
    }

    @Test fun `Product Hunt Atom entries read alternate link, published and content`() {
        val items = TestFeeds.feed("atom-producthunt.xml").items
        assertThat(items).hasSize(10)
        items.forEach {
            assertThat(it.link).startsWith("https://www.producthunt.com/")
            assertThat(it.published).isNotNull()
        }
    }

    @Test fun `stops after 50 items`() {
        assertThat(TestFeeds.feed("150-items.xml").items).hasSize(50)
    }

    @Test fun `empty channel is a feed with no items`() {
        assertThat(TestFeeds.feed("empty-channel.xml").items).isEmpty()
    }

    @Test fun `BOM and leading whitespace are fine`() {
        assertThat(TestFeeds.feed("bom-and-leading-space.xml").items).hasSize(1)
    }

    @Test fun `control characters are dropped`() {
        val item = TestFeeds.feed("control-chars.xml").items.single()
        assertThat(item.title).isEqualTo("Control chars here ok")
        assertThat(item.description).isEqualTo("Bell and NUL gone � too")
    }

    @Test fun `HTML entities decode`() {
        val item = TestFeeds.feed("html-entities.xml").items.single()
        assertThat(item.title).isEqualTo("Founders’ guide — part 1 & more &bogus; R&D")
        assertThat(item.link).isEqualTo("https://example.com/b?a=1&b=2")
    }

    @Test fun `truncated document keeps complete items`() {
        val feed = TestFeeds.feed("truncated-mid-item.xml")
        assertThat(feed.items).hasSize(2)
        assertThat(feed.truncated).isTrue()
    }

    @Test fun `HTML challenge page is not a feed`() {
        assertThat(TestFeeds.parse("cloudflare-challenge.html")).isEqualTo(ParseResult.NotAFeed)
    }

    @Test fun `garbage is a parse failure`() {
        assertThat(TestFeeds.parse("not xml at all".toByteArray())).isInstanceOf(ParseResult.Failed::class.java)
        assertThat(TestFeeds.parse(ByteArray(0))).isInstanceOf(ParseResult.Failed::class.java)
    }

    @Test fun `latin-1 declared feed decodes`() {
        assertThat(TestFeeds.feed("latin1-declared.xml").items.single().title).isEqualTo("Café founders in São Paulo")
    }

    @Test fun `huge description is capped at 64 KB`() {
        val items = TestFeeds.feed("huge-description.xml").items
        assertThat(items).hasSize(2)
        assertThat(items[0].description!!.length).isEqualTo(FeedParser.MAX_TEXT)
    }

    @Test fun `missing and odd dates`() {
        val items = TestFeeds.feed("missing-and-odd-dates.xml").items
        assertThat(items.map { FeedDates.parse(it.published) != null }).containsExactly(false, true, true, false, true).inOrder()
        assertThat(FeedDates.parse(items[1].published)).isEqualTo(FeedDates.parse(items[2].published))
    }

    @Test fun `RSS guid isPermaLink false is recorded`() {
        val xml = """<rss><channel><item><title>t</title><guid isPermaLink="false">abc</guid></item></channel></rss>"""
        val item = (TestFeeds.parse(xml.toByteArray()) as ParseResult.Feed).items.single()
        assertThat(item.guidIsPermaLink).isFalse()
    }

    @Test fun `RSS 1 RDF items under the root are read`() {
        val xml = """<rdf:RDF xmlns:rdf="r"><channel><title>c</title></channel><item><title>t</title><link>https://a/b</link></item></rdf:RDF>"""
        assertThat((TestFeeds.parse(xml.toByteArray()) as ParseResult.Feed).items.single().link).isEqualTo("https://a/b")
    }
}
