// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.parse

/**
 * A small HTML-to-text scanner for titles and snippets (LLD §4.4). Not a renderer: tags are removed, the contents of
 * `script`/`style` are dropped, block-ish tags (`br`, `p`, `div`, `li`, headings) become spaces, and entities are decoded.
 * The result is not whitespace-collapsed; callers do that.
 */
object Html {
    private val BLOCK_TAGS = setOf("br", "p", "div", "li", "ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6", "tr", "td", "blockquote", "figure")
    private val DROP_CONTENT = setOf("script", "style")

    fun toText(html: String): String {
        if (html.indexOf('<') < 0 && html.indexOf('&') < 0) return html
        val sb = StringBuilder(html.length)
        var i = 0
        val n = html.length
        while (i < n) {
            val c = html[i]
            if (c == '<') {
                // Comments.
                if (html.startsWith("<!--", i)) {
                    val end = html.indexOf("-->", i + 4)
                    i = if (end < 0) n else end + 3
                    continue
                }
                val close = html.indexOf('>', i + 1)
                if (close < 0) {
                    // A lone '<' (e.g. "a < b"): keep it as text.
                    sb.append(c)
                    i++
                    continue
                }
                val name = tagName(html, i + 1, close)
                if (name == null) {
                    sb.append(c)
                    i++
                    continue
                }
                val lower = name.lowercase()
                val isEnd = html[i + 1] == '/'
                if (!isEnd && lower in DROP_CONTENT) {
                    val endTag = html.indexOf("</$lower", close, ignoreCase = true)
                    i = if (endTag < 0) n else (html.indexOf('>', endTag).takeIf { it >= 0 }?.plus(1) ?: n)
                    continue
                }
                if (lower in BLOCK_TAGS) sb.append(' ')
                i = close + 1
                continue
            }
            if (c == '&') {
                val semi = html.indexOf(';', i + 1)
                if (semi in (i + 2)..(i + 12)) {
                    val decoded = decodeEntity(html.substring(i + 1, semi))
                    if (decoded != null) {
                        sb.appendCodePoint(decoded)
                        i = semi + 1
                        continue
                    }
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    /** Returns the tag name after `<` or `</`, or null if this isn't a tag (so the `<` is literal text). */
    private fun tagName(s: String, from: Int, limit: Int): String? {
        var i = from
        if (i < limit && (s[i] == '/' || s[i] == '!' || s[i] == '?')) i++
        val start = i
        while (i < limit && (s[i].isLetterOrDigit() || s[i] == ':' || s[i] == '-')) i++
        if (i == start || !s[start].isLetter()) return null
        return s.substring(start, i)
    }

    private fun decodeEntity(name: String): Int? {
        if (name.startsWith("#")) {
            val hex = name.length > 1 && (name[1] == 'x' || name[1] == 'X')
            val digits = if (hex) name.substring(2) else name.substring(1)
            val cp = digits.toIntOrNull(if (hex) 16 else 10) ?: return null
            return if (HtmlEntities.isXmlChar(cp)) cp else 0xFFFD
        }
        return HtmlEntities.XML[name] ?: HtmlEntities.NAMED[name]
    }

    private val WS = Regex("[\\s\\u00A0\\u2000-\\u200A\\u202F\\u205F\\u3000]+")

    /** Collapses every run of whitespace (including no-break and thin spaces) into one space, and trims. */
    fun collapse(text: String): String = text.replace(WS, " ").trim()
}
