// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.parse

import io.github.ukpratik.founderguru.core.model.MediaRef
import io.github.ukpratik.founderguru.core.model.ParsedItem
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Picks one thumbnail URL per item (LLD §4.4), in order: `media:content` (image) → `media:thumbnail` → an image
 * `enclosure` → the first `<img src>` in the description, then in `content:encoded` / Atom content. Skips `data:` URIs,
 * tracking pixels (≤ 2 px) and non-http(s) URLs. `http://` is upgraded to `https://` (NFR-007).
 */
object ImageExtractor {
    private val IMAGE_EXT = Regex("""\.(?:jpe?g|png|gif|webp|avif)(?:$|[?#])""", RegexOption.IGNORE_CASE)
    private val IMG_TAG = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val SRC = Regex("""\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
    private val DIM = Regex("""\b(width|height)\s*=\s*["']?(\d+)""", RegexOption.IGNORE_CASE)

    fun extract(item: ParsedItem, baseUrl: String? = null): String? {
        val byKind = item.media.groupBy { it.kind }
        val candidates = sequence {
            byKind[MediaRef.Kind.MEDIA_CONTENT].orEmpty().filter { isImageMedia(it) }.forEach { yield(it.url to it.tiny()) }
            byKind[MediaRef.Kind.MEDIA_THUMBNAIL].orEmpty().forEach { yield(it.url to it.tiny()) }
            byKind[MediaRef.Kind.ENCLOSURE].orEmpty().filter { it.type?.startsWith("image/", ignoreCase = true) == true }
                .forEach { yield(it.url to false) }
            for (html in listOf(item.description, item.encodedContent, item.atomContent)) {
                if (html != null) yieldAll(imgTags(html))
            }
        }
        for ((url, tiny) in candidates) {
            if (tiny) continue
            clean(url, baseUrl)?.let { return it }
        }
        return null
    }

    private fun isImageMedia(m: MediaRef): Boolean =
        m.medium.equals("image", ignoreCase = true) ||
            m.type?.startsWith("image/", ignoreCase = true) == true ||
            (m.medium == null && m.type == null && IMAGE_EXT.containsMatchIn(m.url))

    private fun MediaRef.tiny(): Boolean = isTiny(width) || isTiny(height)

    private fun isTiny(v: String?): Boolean = v?.trim()?.toIntOrNull()?.let { it <= 2 } ?: false

    private fun imgTags(html: String): Sequence<Pair<String, Boolean>> = IMG_TAG.findAll(html).mapNotNull { tag ->
        val m = SRC.find(tag.value) ?: return@mapNotNull null
        val src = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() } ?: return@mapNotNull null
        val tiny = DIM.findAll(tag.value).any { it.groupValues[2].toInt() <= 2 }
        Html.toText(src) to tiny
    }

    private fun clean(raw: String, baseUrl: String?): String? {
        val s = raw.trim()
        if (s.isEmpty() || s.startsWith("data:", ignoreCase = true)) return null
        val url = baseUrl?.toHttpUrlOrNull()?.resolve(s) ?: s.toHttpUrlOrNull() ?: return null
        val https = if (url.scheme == "http") url.newBuilder().scheme("https").build() else url
        return https.toString().takeIf { it.length <= 2048 }
    }
}
