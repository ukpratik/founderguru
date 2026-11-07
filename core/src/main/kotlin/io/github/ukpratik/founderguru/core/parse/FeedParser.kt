// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.parse

import io.github.ukpratik.founderguru.core.fetch.BodyTooLargeException
import io.github.ukpratik.founderguru.core.model.MediaRef
import io.github.ukpratik.founderguru.core.model.ParsedItem
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.Reader

sealed interface ParseResult {
    /** [truncated]: the document broke (or hit the size cap) after at least one complete item, which are kept. */
    data class Feed(val items: List<ParsedItem>, val truncated: Boolean) : ParseResult
    data object NotAFeed : ParseResult
    data object TooLarge : ParseResult
    data class Failed(val message: String) : ParseResult
}

/**
 * RSS 2.0 / RSS 1.0 (RDF) / Atom pull parser (LLD §4.3). Namespace processing is off, so names are raw qualified names
 * (`media:content`) and an undeclared prefix (Condia) is not an error. The [parserFactory] is the platform parser on
 * Android and kxml2 on the JVM. Feed the input through [CleaningReader] first.
 * I/O errors other than the size cap propagate to the caller.
 */
class FeedParser(private val parserFactory: () -> XmlPullParser, private val maxItems: Int = MAX_ITEMS) {

    fun parse(reader: Reader): ParseResult {
        val items = ArrayList<ParsedItem>()
        return try {
            val p = parserFactory()
            setFeature(p, XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setFeature(p, XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
            p.setInput(reader)
            val root = firstStartTag(p) ?: return ParseResult.Failed("no root element")
            val atom = when (root) {
                "rss", "rdf:RDF" -> false
                "feed" -> true
                else -> return ParseResult.NotAFeed
            }
            val itemTag = if (atom) "entry" else "item"
            var event = p.next()
            while (event != XmlPullParser.END_DOCUMENT && items.size < maxItems) {
                if (event == XmlPullParser.START_TAG && p.name == itemTag) items += readItem(p, itemTag, atom)
                event = p.next()
            }
            ParseResult.Feed(items, truncated = false)
        } catch (_: BodyTooLargeException) {
            if (items.isEmpty()) ParseResult.TooLarge else ParseResult.Feed(items, truncated = true)
        } catch (e: XmlPullParserException) {
            if (items.isEmpty()) ParseResult.Failed(e.message.orEmpty().take(200)) else ParseResult.Feed(items, truncated = true)
        } catch (e: RuntimeException) {
            // kxml2 throws a few unchecked exceptions on pathological input; they're parse failures, not crashes (NFR-003).
            if (items.isEmpty()) ParseResult.Failed(e.toString().take(200)) else ParseResult.Feed(items, truncated = true)
        }
    }

    private fun setFeature(p: XmlPullParser, name: String, value: Boolean) {
        try {
            p.setFeature(name, value)
        } catch (_: XmlPullParserException) {
            // Defaults are already off on both parsers; a parser that refuses the call is fine.
        }
    }

    private fun firstStartTag(p: XmlPullParser): String? {
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) return p.name
            event = p.next()
        }
        return null
    }

    /** Reads one item; the parser is on its START_TAG and ends on its END_TAG. */
    private fun readItem(p: XmlPullParser, itemTag: String, atom: Boolean): ParsedItem {
        val itemDepth = p.depth
        val fields = HashMap<String, String>()
        val media = ArrayList<MediaRef>()
        var guidIsPermaLink = true
        var atomLink: String? = null
        var field: String? = null
        val text = StringBuilder()
        while (true) {
            when (p.next()) {
                XmlPullParser.START_TAG -> {
                    val name = p.name
                    if (p.depth == itemDepth + 1) {
                        field = name
                        text.setLength(0)
                        if (name == "guid") guidIsPermaLink = p.getAttributeValue(null, "isPermaLink")?.trim() != "false"
                        if (atom && name == "link" && atomLink == null) {
                            val rel = p.getAttributeValue(null, "rel")
                            if (rel == null || rel == "alternate") atomLink = p.getAttributeValue(null, "href")
                        }
                    }
                    mediaRef(p, name)?.let { media += it }
                }
                XmlPullParser.TEXT -> if (field != null && text.length < MAX_TEXT) {
                    val t = p.text
                    text.append(t, 0, minOf(t.length, MAX_TEXT - text.length))
                }
                XmlPullParser.END_TAG -> {
                    if (p.depth == itemDepth) {
                        if (p.name == itemTag) break
                    } else if (p.depth == itemDepth + 1 && field != null) {
                        // First occurrence wins (e.g. Atom `published` over a later duplicate).
                        fields.putIfAbsent(field, text.toString())
                        field = null
                    }
                }
                XmlPullParser.END_DOCUMENT -> throw XmlPullParserException("unexpected end of document inside $itemTag")
            }
        }
        fun f(vararg names: String): String? = names.firstNotNullOfOrNull { n -> fields[n]?.takeIf { it.isNotBlank() } }
        return if (atom) {
            ParsedItem(
                title = f("title"),
                link = atomLink,
                atomId = f("id"),
                published = f("published", "updated", "dc:date"),
                description = f("summary"),
                atomContent = f("content"),
                media = media,
            )
        } else {
            ParsedItem(
                title = f("title"),
                link = f("link"),
                guid = f("guid"),
                guidIsPermaLink = guidIsPermaLink,
                published = f("pubDate", "dc:date", "published", "updated"),
                description = f("description"),
                encodedContent = f("content:encoded"),
                media = media,
            )
        }
    }

    private fun mediaRef(p: XmlPullParser, name: String): MediaRef? {
        val kind = when (name) {
            "media:content" -> MediaRef.Kind.MEDIA_CONTENT
            "media:thumbnail" -> MediaRef.Kind.MEDIA_THUMBNAIL
            "enclosure" -> MediaRef.Kind.ENCLOSURE
            else -> return null
        }
        val url = p.getAttributeValue(null, "url")?.takeIf { it.isNotBlank() } ?: return null
        return MediaRef(
            kind = kind,
            url = url,
            medium = p.getAttributeValue(null, "medium"),
            type = p.getAttributeValue(null, "type"),
            width = p.getAttributeValue(null, "width"),
            height = p.getAttributeValue(null, "height"),
        )
    }

    companion object {
        /** Reading stops after this many items (US-004). */
        const val MAX_ITEMS = 50

        /** Per-field text cap (LLD §4.3). */
        const val MAX_TEXT = 64 * 1024
    }
}
