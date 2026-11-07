// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.normalise

import io.github.ukpratik.founderguru.core.parse.Html

/** Builds the stored snippet: plain text, no boilerplate, at most 200 characters (LLD §4.4, NFR-010). */
object Snippet {
    const val MAX = 200
    private const val ELLIPSIS = '…'

    private val BOILERPLATE = listOf(
        Regex("""The post .{1,400}? appeared first on .{1,200}?\.?\s*$""", RegexOption.IGNORE_CASE),
        Regex("""Continue reading\s*(?:…|\.\.\.)?.*$""", RegexOption.IGNORE_CASE),
        Regex("""\bRead more\b\s*(?:»|›|→|…|\.\.\.)?\s*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*\[(?:…|\.\.\.|&hellip;)]\s*$"""),
        // hnrss.org item bodies.
        Regex("""(?:Article URL|Comments URL|Points|# Comments):\s*\S*""", RegexOption.IGNORE_CASE),
    )

    fun of(raw: String?, title: String): String {
        if (raw.isNullOrBlank()) return ""
        var text = Html.collapse(Html.toText(raw))
        for (re in BOILERPLATE) text = re.replace(text, " ").let(Html::collapse)
        if (text.equals(title, ignoreCase = true)) return ""
        return cut(text)
    }

    /** Cuts at the last whitespace at or before index 199 and appends `…`, so the result is at most [MAX] characters. */
    fun cut(text: String): String {
        if (text.length <= MAX) return text
        val limit = MAX - 1
        var i = limit
        while (i > 0 && !text[i].isWhitespace()) i--
        val head = if (i == 0) text.substring(0, limit) else text.substring(0, i)
        return head.trimEnd() + ELLIPSIS
    }
}
