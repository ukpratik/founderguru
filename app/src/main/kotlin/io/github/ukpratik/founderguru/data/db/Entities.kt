// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One cached story (LLD §2.1). Times are epoch ms UTC. */
@Entity(
    tableName = "stories",
    indices = [
        Index(value = ["urlKey"], unique = true),
        Index("titleKey"),
        Index("sourceId"),
        Index("publishedAt"),
        Index("firstSeenAt"),
        Index("savedAt"),
    ],
)
data class StoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String,
    val sourceName: String,
    val url: String,
    val urlKey: String,
    val title: String,
    val titleKey: String,
    val snippet: String,
    val imageUrl: String?,
    val publishedAt: Long,
    val firstSeenAt: Long,
    /** Bitmask: Funding 1, Launches 2, Trends 4, Playbooks 8 (set by the tagger from M2). */
    val lenses: Int = 0,
    /** Bitmask over `Sector` ordinals (append-only, from M2). */
    val sectors: Long = 0,
    /** Bitmask IN 1, EU 2, NA 4, AF 8, SEA 16, LATAM 32. Global sources only (from M2). */
    val regionMentions: Int = 0,
    val savedAt: Long? = null,
    val openedAt: Long? = null,
)

/** Per-source state (LLD §2.1). `enabled` is user-owned; the rest is refresh/health state. */
@Entity(tableName = "source_state")
data class SourceStateEntity(
    @PrimaryKey val id: String,
    val region: String,
    val url: String,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
    val etag: String? = null,
    val lastModified: String? = null,
    val finalUrl: String? = null,
    val lastStatus: String? = null,
    val lastHttpCode: Int? = null,
    val lastError: String? = null,
    val lastAttemptAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val newestItemAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val consecutiveFailures: Int = 0,
)

/** A title-dedup candidate: an existing story plus its source's region. */
data class TitleMatch(val id: Long, val region: String?, val savedAt: Long?)

/** Input for re-tagging after a rule update (LLD §4.10). */
data class RetagRow(val id: Long, val sourceId: String, val title: String, val snippet: String)
