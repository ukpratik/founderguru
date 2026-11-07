// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import io.github.ukpratik.founderguru.core.model.StoryRow
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {
    /** INSERT OR IGNORE on the unique `urlKey`: returns -1 for a duplicate; `firstSeenAt` is never overwritten. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(story: StoryEntity): Long

    @Query("SELECT urlKey FROM stories WHERE urlKey IN (:keys)")
    suspend fun existingUrlKeys(keys: List<String>): List<String>

    /** Stories with the same title key within ±[windowMs] of [publishedAt], with their source's region (null = removed). */
    @Query(
        """SELECT st.id AS id, s.region AS region, st.savedAt AS savedAt FROM stories st
           LEFT JOIN source_state s ON s.id = st.sourceId
           WHERE st.titleKey = :titleKey AND st.publishedAt BETWEEN :publishedAt - :windowMs AND :publishedAt + :windowMs""",
    )
    suspend fun titleMatches(titleKey: String, publishedAt: Long, windowMs: Long): List<TitleMatch>

    @Query("DELETE FROM stories WHERE id = :id AND savedAt IS NULL")
    suspend fun deleteUnsaved(id: Long): Int

    @Query("SELECT publishedAt FROM stories WHERE sourceId = :sourceId AND publishedAt >= :since")
    suspend fun publishedSince(sourceId: String, since: Long): List<Long>

    @Query("SELECT * FROM stories WHERE sourceId = :sourceId ORDER BY publishedAt DESC")
    suspend fun bySource(sourceId: String): List<StoryEntity>

    @Query("SELECT COUNT(*) FROM stories")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM stories WHERE savedAt IS NOT NULL")
    suspend fun savedCount(): Int

    @Query("UPDATE stories SET savedAt = :savedAt WHERE id = :id")
    suspend fun setSavedAt(id: Long, savedAt: Long?)

    /** Visited state (D-017): set when the story is opened. */
    @Query("UPDATE stories SET openedAt = :openedAt WHERE id = :id")
    suspend fun setOpenedAt(id: Long, openedAt: Long)

    /** NFR-004: unsaved stories older than the window go. Saved stories are never pruned (D-023 G-5). */
    @Query("DELETE FROM stories WHERE savedAt IS NULL AND publishedAt < :before")
    suspend fun pruneOlderThan(before: Long): Int

    /** NFR-004: keep the newest [limit] unsaved stories (the 2,000 cap counts unsaved stories only, D-023 G-5). */
    @Query(
        """DELETE FROM stories WHERE savedAt IS NULL AND id NOT IN
           (SELECT id FROM stories WHERE savedAt IS NULL ORDER BY publishedAt DESC, id DESC LIMIT :limit)""",
    )
    suspend fun pruneUnsavedBeyond(limit: Int): Int

    /** A tab's own rows: enabled sources of [region], 14-day window (LLD §3). Turning a source off hides its stories. */
    @Query(
        """SELECT $ROW FROM stories st JOIN source_state s ON s.id = st.sourceId
           WHERE s.enabled = 1 AND s.region = :region AND st.publishedAt >= :since ORDER BY st.publishedAt DESC, st.id DESC""",
    )
    fun regionWindow(region: String, since: Long): Flow<List<StoryRow>>

    /** Local's via-Global candidates: enabled Global sources whose stories mention the region (LLD §3, §4.8). */
    @Query(
        """SELECT $ROW FROM stories st JOIN source_state s ON s.id = st.sourceId
           WHERE s.enabled = 1 AND s.region = 'GLOBAL' AND (st.regionMentions & :regionBit) != 0 AND st.publishedAt >= :since
           ORDER BY st.publishedAt DESC, st.id DESC""",
    )
    fun viaGlobalCandidates(regionBit: Int, since: Long): Flow<List<StoryRow>>

    @Query("SELECT id, sourceId, title, snippet FROM stories")
    suspend fun allForRetag(): List<RetagRow>

    @Query("UPDATE stories SET lenses = :lenses, sectors = :sectors, regionMentions = :regionMentions WHERE id = :id")
    suspend fun updateTags(id: Long, lenses: Int, sectors: Long, regionMentions: Int)

    @Query("DELETE FROM stories WHERE savedAt IS NULL AND sourceId NOT IN (:keepIds)")
    suspend fun deleteUnsavedNotIn(keepIds: List<String>): Int
}

/** The `StoryRow` projection columns (LLD §3); `viaGlobal` is set by the blend, never stored. */
private const val ROW = """st.id, st.sourceId, st.sourceName, st.url, st.title, st.snippet, st.imageUrl, st.publishedAt,
    st.firstSeenAt, st.lenses, st.sectors, st.regionMentions, st.savedAt, st.openedAt, 0 AS viaGlobal"""

@Dao
interface SourceStateDao {
    @Query("SELECT * FROM source_state")
    suspend fun all(): List<SourceStateEntity>

    /** Health and enabled state for the feed states (US-011); emits on every refresh write. */
    @Query("SELECT * FROM source_state")
    fun observeAll(): Flow<List<SourceStateEntity>>

    @Query("SELECT * FROM source_state WHERE id = :id")
    suspend fun byId(id: String): SourceStateEntity?

    @Upsert
    suspend fun upsert(state: SourceStateEntity)

    @Query("DELETE FROM source_state WHERE id NOT IN (:keepIds)")
    suspend fun deleteNotIn(keepIds: List<String>): Int

    @Query("UPDATE source_state SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
}
