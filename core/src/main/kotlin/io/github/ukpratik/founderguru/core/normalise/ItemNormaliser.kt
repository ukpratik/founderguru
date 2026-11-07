// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.normalise

import io.github.ukpratik.founderguru.core.model.ParsedItem
import io.github.ukpratik.founderguru.core.model.StoryDraft
import io.github.ukpratik.founderguru.core.parse.FeedDates
import io.github.ukpratik.founderguru.core.parse.Html
import io.github.ukpratik.founderguru.core.parse.ImageExtractor

/** [ParsedItem] → [StoryDraft], or null to drop the item (LLD §4.4). Pure; [now] is injected. */
object ItemNormaliser {
    const val MAX_TITLE = 300
    const val WINDOW_MS = 14L * 24 * 60 * 60 * 1000

    fun normalise(item: ParsedItem, feedUrl: String, now: Long): StoryDraft? {
        val link = item.link?.takeIf { it.isNotBlank() }
            ?: item.guid?.takeIf { item.guidIsPermaLink && it.isNotBlank() }
            ?: item.atomId?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            ?: return null
        val url = UrlNormaliser.clean(Html.toText(link), feedUrl) ?: return null
        val urlKey = UrlNormaliser.key(url) ?: return null

        val title = Html.collapse(Html.toText(item.title.orEmpty())).take(MAX_TITLE).trim()
        if (title.isEmpty()) return null

        val rawSnippet = listOf(item.description, item.atomContent).firstOrNull { !it.isNullOrBlank() }
        val published = FeedDates.parse(item.published)?.coerceAtMost(now) ?: now
        if (published < now - WINDOW_MS) return null

        return StoryDraft(
            url = url,
            urlKey = urlKey,
            title = title,
            titleKey = TitleKey.of(title),
            snippet = Snippet.of(rawSnippet, title),
            imageUrl = ImageExtractor.extract(item, feedUrl),
            publishedAt = published,
        )
    }
}
