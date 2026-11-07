// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.model

import kotlinx.serialization.Serializable

/**
 * The 6 v1 regions plus GLOBAL (LLD §2.3). Stored by name; never rename. [mentionBit] is the `stories.regionMentions`
 * bit (LLD §2.1); GLOBAL has none.
 */
@Serializable
enum class Region(val mentionBit: Int) { GLOBAL(0), IN(1), EU(2), NA(4), AF(8), SEA(16), LATAM(32) }

/** Founder lenses (US-005). [bit] is the `stories.lenses` bitmask value (LLD §2.1). */
@Serializable
enum class Lens(val bit: Int) { FUNDING(1), LAUNCHES(2), TRENDS(4), PLAYBOOKS(8) }

/** One entry of `assets/sources.json` (LLD §2.3, NFR-011). */
@Serializable
data class SourceDef(
    val id: String,
    val name: String,
    val region: Region,
    val url: String,
    val homepage: String,
    val defaultLens: Lens? = null,
    val dailyCap: Int? = null,
    val needsKeywordFilter: Boolean = false,
    val staleAfterDays: Int = 14,
)

@Serializable
data class SourcesCatalog(
    val schemaVersion: Int,
    val listVersion: Int,
    val sources: List<SourceDef>,
)

/** A media/enclosure reference found inside an item, kept raw for [io.github.ukpratik.founderguru.core.parse.ImageExtractor]. */
data class MediaRef(
    val kind: Kind,
    val url: String,
    val medium: String? = null,
    val type: String? = null,
    val width: String? = null,
    val height: String? = null,
) {
    enum class Kind { MEDIA_CONTENT, MEDIA_THUMBNAIL, ENCLOSURE }
}

/**
 * One RSS `item` or Atom `entry`, as read from the feed (LLD §4.3). Text fields are raw (may hold HTML) and capped at 64 KB.
 * [description] is the RSS `description` or the Atom `summary`; [atomContent] is the Atom `content`; [encodedContent] is RSS
 * `content:encoded`, which is only ever scanned for an image and never stored.
 */
data class ParsedItem(
    val title: String? = null,
    val link: String? = null,
    val guid: String? = null,
    val guidIsPermaLink: Boolean = true,
    val atomId: String? = null,
    val published: String? = null,
    val description: String? = null,
    val atomContent: String? = null,
    val encodedContent: String? = null,
    val media: List<MediaRef> = emptyList(),
)

/** A normalised item, ready for de-duplication and storage (LLD §4.4). Times are epoch ms UTC. */
data class StoryDraft(
    val url: String,
    val urlKey: String,
    val title: String,
    val titleKey: String,
    val snippet: String,
    val imageUrl: String?,
    val publishedAt: Long,
)

/** HTTP state carried between refreshes for one source (LLD §3). */
data class HttpState(val etag: String? = null, val lastModified: String? = null, val finalUrl: String? = null)

/** Stored as `source_state.lastStatus` by name (with `OK` / `NOT_MODIFIED`); never rename. */
enum class FailureKind { TIMEOUT, NETWORK, HTTP, BLOCKED, REDIRECTS, INSECURE, NOT_A_FEED, PARSE, TOO_LARGE }

sealed interface FetchOutcome {
    data class Updated(
        val items: List<ParsedItem>,
        val etag: String?,
        val lastModified: String?,
        /** Set only when every redirect hop was permanent (301/308); stored as `finalUrl`. */
        val permanentUrl: String?,
        /** The final URL the body came from (for resolving relative links). */
        val fetchedUrl: String,
        val truncated: Boolean,
    ) : FetchOutcome

    data object NotModified : FetchOutcome

    data class Failed(val kind: FailureKind, val httpCode: Int?, val message: String) : FetchOutcome
}

/** The read model for lists (LLD §3): a `stories` projection. [viaGlobal] is set only by the Local blend. */
data class StoryRow(
    val id: Long,
    val sourceId: String,
    val sourceName: String,
    val url: String,
    val title: String,
    val snippet: String,
    val imageUrl: String?,
    val publishedAt: Long,
    val firstSeenAt: Long,
    val lenses: Int,
    val sectors: Long,
    val regionMentions: Int,
    val savedAt: Long?,
    val openedAt: Long?,
    val viaGlobal: Boolean = false,
)
