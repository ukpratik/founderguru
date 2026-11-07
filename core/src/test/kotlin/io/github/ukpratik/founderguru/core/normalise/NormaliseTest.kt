// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.normalise

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.ukpratik.founderguru.core.model.ParsedItem
import io.github.ukpratik.founderguru.core.parse.FeedDates
import org.junit.Test

class UrlNormaliserTest {
    @Test fun `key table`() {
        val cases = listOf(
            "https://www.inc42.com/buzz/x/?utm_source=rss#c" to "inc42.com/buzz/x",
            "http://inc42.com/buzz/x" to "inc42.com/buzz/x",
            "https://WWW.Example.COM/A/B/" to "example.com/A/B",
            "https://example.com/" to "example.com/",
            "https://example.com" to "example.com/",
            "https://example.com:8443/a" to "example.com:8443/a",
            "https://example.com:443/a" to "example.com/a",
            "https://example.com/a?id=2&UTM_Medium=x&fbclid=1&ref=hn&b=3" to "example.com/a?id=2&b=3",
            "https://example.com/a?refx=1" to "example.com/a?refx=1",
        )
        for ((url, key) in cases) assertWithMessage(url).that(UrlNormaliser.key(url)).isEqualTo(key)
    }

    @Test fun `clean resolves relative links and strips tracking but keeps the fragment`() {
        assertThat(UrlNormaliser.clean("/a/b?utm_source=x&id=1#top", "https://site.com/feed/")).isEqualTo("https://site.com/a/b?id=1#top")
        assertThat(UrlNormaliser.clean("https://x.com/p?utm_campaign=1", null)).isEqualTo("https://x.com/p")
    }

    @Test fun `clean drops non-http links and overlong URLs`() {
        assertThat(UrlNormaliser.clean("mailto:a@b.c", "https://site.com/")).isNull()
        assertThat(UrlNormaliser.clean("javascript:alert(1)", null)).isNull()
        assertThat(UrlNormaliser.clean("  ", null)).isNull()
        assertThat(UrlNormaliser.clean("https://x.com/" + "a".repeat(2100), null)).isNull()
    }
}

class TitleKeyTest {
    @Test fun normalises() {
        assertThat(TitleKey.of("Zepto Raises \$450M — Series G, Led by Café Fund!")).isEqualTo("zepto raises 450m series g led by cafe fund")
        assertThat(TitleKey.of("  São  Paulo's  fintech  ")).isEqualTo("sao paulo s fintech")
    }

    @Test fun `short titles are not used for de-duplication`() {
        assertThat(TitleKey.isDedupable(TitleKey.of("Weekly funding roundup"))).isFalse()
        assertThat(TitleKey.isDedupable(TitleKey.of("Zepto raises big round"))).isTrue()
        assertThat(TitleKey.isDedupable("")).isFalse()
    }
}

class SnippetTest {
    @Test fun `strips HTML, scripts and boilerplate`() {
        val raw = "<p>Hello <b>founders</b>&nbsp;&amp; friends.</p><script>var x = 1;</script><br/>Next line [&#8230;]" +
            "<p>The post <a href='x'>Hello</a> appeared first on Inc42 Media.</p>"
        assertThat(Snippet.of(raw, "Title")).isEqualTo("Hello founders & friends. Next line")
    }

    @Test fun `hnrss boilerplate lines are removed`() {
        val raw = "<p>Article URL: <a href=\"https://x\">https://x</a></p><p>Comments URL: <a href=\"https://y\">https://y</a></p><p>Points: 52</p><p># Comments: 7</p>"
        assertThat(Snippet.of(raw, "t")).isEmpty()
    }

    @Test fun `continue reading and read more are removed`() {
        assertThat(Snippet.of("Some text. Continue reading…", "t")).isEqualTo("Some text.")
        assertThat(Snippet.of("Some text. Read more »", "t")).isEqualTo("Some text.")
    }

    @Test fun `snippet equal to title becomes empty`() {
        assertThat(Snippet.of("<p>Same Title</p>", "same title")).isEmpty()
    }

    @Test fun `cut at a word boundary, at most 200 characters with ellipsis (D-023 G-11)`() {
        val words = "word ".repeat(60).trim()
        val cut = Snippet.of(words, "t")
        assertThat(cut.length).isAtMost(200)
        assertThat(cut).endsWith("word…")
        assertThat(Snippet.cut("a".repeat(150) + " " + "b".repeat(100))).isEqualTo("a".repeat(150) + "…")
    }

    @Test fun `no whitespace means a hard cut at 199 plus ellipsis (D-023 G-11)`() {
        val cut = Snippet.cut("x".repeat(500))
        assertThat(cut).isEqualTo("x".repeat(199) + "…")
        assertThat(cut.length).isEqualTo(200)
    }

    @Test fun `short text is unchanged`() {
        assertThat(Snippet.cut("x".repeat(200))).hasLength(200)
    }
}

class ItemNormaliserTest {
    private val now = FeedDates.parse("2026-11-07T12:00:00Z")!!

    @Test fun `full item`() {
        val d = ItemNormaliser.normalise(
            ParsedItem(
                title = "Zepto &amp; Co <b>raise</b>\n \$450M",
                link = "/story/1?utm_source=rss",
                published = "Fri, 06 Nov 2026 10:15:00 GMT",
                description = "<p>Body <img src=\"http://cdn.x.com/a.jpg\" width=\"600\"/></p>",
            ),
            feedUrl = "https://www.news.com/feed/",
            now = now,
        )!!
        assertThat(d.url).isEqualTo("https://www.news.com/story/1")
        assertThat(d.urlKey).isEqualTo("news.com/story/1")
        assertThat(d.title).isEqualTo("Zepto & Co raise \$450M")
        assertThat(d.titleKey).isEqualTo("zepto co raise 450m")
        assertThat(d.snippet).isEqualTo("Body")
        assertThat(d.imageUrl).isEqualTo("https://cdn.x.com/a.jpg")
        assertThat(d.publishedAt).isEqualTo(FeedDates.parse("2026-11-06T10:15:00Z"))
    }

    @Test fun `link falls back to a permalink guid, then an http Atom id`() {
        val base = ParsedItem(title = "t")
        assertThat(ItemNormaliser.normalise(base.copy(guid = "https://a.com/g"), "https://a.com/", now)!!.url).isEqualTo("https://a.com/g")
        assertThat(ItemNormaliser.normalise(base.copy(guid = "https://a.com/g", guidIsPermaLink = false), "https://a.com/", now)).isNull()
        assertThat(ItemNormaliser.normalise(base.copy(atomId = "tag:x,2026:1"), "https://a.com/", now)).isNull()
        assertThat(ItemNormaliser.normalise(base.copy(atomId = "https://a.com/i"), "https://a.com/", now)!!.url).isEqualTo("https://a.com/i")
    }

    @Test fun `drops items without a title or with a non-http link`() {
        assertThat(ItemNormaliser.normalise(ParsedItem(title = " <b></b> ", link = "https://a.com/x"), "https://a.com/", now)).isNull()
        assertThat(ItemNormaliser.normalise(ParsedItem(title = "t", link = "ftp://a.com/x"), "https://a.com/", now)).isNull()
    }

    @Test fun `dates - missing and future become now, older than 14 days are dropped`() {
        val item = ParsedItem(title = "t", link = "https://a.com/x")
        assertThat(ItemNormaliser.normalise(item, "https://a.com/", now)!!.publishedAt).isEqualTo(now)
        assertThat(ItemNormaliser.normalise(item.copy(published = "2050-01-01T00:00:00Z"), "https://a.com/", now)!!.publishedAt).isEqualTo(now)
        assertThat(ItemNormaliser.normalise(item.copy(published = "2026-10-20T00:00:00Z"), "https://a.com/", now)).isNull()
    }

    @Test fun `title is cut to 300 characters`() {
        val d = ItemNormaliser.normalise(ParsedItem(title = "a".repeat(400), link = "https://a.com/x"), "https://a.com/", now)!!
        assertThat(d.title).hasLength(300)
    }

    @Test fun `Atom summary first, then Atom content, never content-encoded`() {
        val item = ParsedItem(title = "t", link = "https://a.com/x", atomContent = "from content", encodedContent = "full text")
        assertThat(ItemNormaliser.normalise(item, "https://a.com/", now)!!.snippet).isEqualTo("from content")
        assertThat(ItemNormaliser.normalise(item.copy(atomContent = null), "https://a.com/", now)!!.snippet).isEmpty()
    }
}
