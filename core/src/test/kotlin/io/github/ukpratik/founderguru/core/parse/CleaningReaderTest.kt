// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.parse

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.Reader
import java.io.StringReader

class CleaningReaderTest {
    private fun clean(s: String): String = CleaningReader(StringReader(s)).readText()

    /** Returns one character per read call, so every look-ahead crosses a buffer edge. */
    private class Dribble(private val s: String) : Reader() {
        private var i = 0
        override fun read(cbuf: CharArray, off: Int, len: Int): Int {
            if (i >= s.length) return -1
            cbuf[off] = s[i++]
            return 1
        }
        override fun close() = Unit
    }

    @Test fun `drops BOM and anything before the first tag`() {
        assertThat(clean("﻿  junk\n<rss/>")).isEqualTo("<rss/>")
    }

    @Test fun `drops invalid XML characters`() {
        assertThat(clean("<a>x\u0001y\u000Bz\u0000￾w</a>")).isEqualTo("<a>xyzw</a>")
    }

    @Test fun `keeps valid surrogate pairs and drops unpaired ones`() {
        assertThat(clean("<a>🚀 \uD83D x \uDE80</a>")).isEqualTo("<a>🚀  x </a>")
    }

    @Test fun `stray ampersands become amp`() {
        assertThat(clean("<a>R&D and ?a=1&b=2 & more &</a>")).isEqualTo("<a>R&amp;D and ?a=1&amp;b=2 &amp; more &amp;</a>")
    }

    @Test fun `stray ampersand inside an attribute value is escaped`() {
        assertThat(clean("""<img src="https://x/y.jpg?w=1&h=2"/>""")).isEqualTo("""<img src="https://x/y.jpg?w=1&amp;h=2"/>""")
    }

    @Test fun `XML and numeric references pass, HTML named entities become numeric`() {
        assertThat(clean("<a>&amp;&lt;&#65;&#x42;&nbsp;&mdash;&rsquo;</a>")).isEqualTo("<a>&amp;&lt;&#65;&#66;&#160;&#8212;&#8217;</a>")
    }

    @Test fun `uppercase hex references are canonicalised`() {
        assertThat(clean("<a>&#X41;</a>")).isEqualTo("<a>&#65;</a>")
    }

    @Test fun `references to invalid characters become U+FFFD`() {
        assertThat(clean("<a>&#1;&#x0;&#xFFFFFF;</a>")).isEqualTo("<a>���</a>")
    }

    @Test fun `unknown named entities are escaped`() {
        assertThat(clean("<a>&bogus; &toolongentityname;</a>")).isEqualTo("<a>&amp;bogus; &amp;toolongentityname;</a>")
    }

    @Test fun `CDATA and comments pass untouched`() {
        val s = "<a><![CDATA[a & b &nbsp; <b>]]><!-- x & y --></a>"
        assertThat(clean(s)).isEqualTo(s)
    }

    @Test fun `processing instruction passes untouched`() {
        val s = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><a/>"
        assertThat(clean(s)).isEqualTo(s)
    }

    @Test fun `entities split across buffer edges give the same output`() {
        val s = "<a b=\"1&2\">&mdash; &amp; R&D <![CDATA[&x]]> &#x2019;</a>"
        assertThat(CleaningReader(Dribble(s)).readText()).isEqualTo(clean(s))
    }

    @Test fun `quoted greater-than inside an attribute keeps tag state`() {
        assertThat(clean("<a t=\"x>y&z\">q&r</a>")).isEqualTo("<a t=\"x>y&amp;z\">q&amp;r</a>")
    }
}
