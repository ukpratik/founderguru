// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.data

import androidx.room.withTransaction
import io.github.ukpratik.founderguru.core.feed.Blender
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.SourcesCatalog
import io.github.ukpratik.founderguru.core.model.StoryRow
import io.github.ukpratik.founderguru.core.normalise.ItemNormaliser
import io.github.ukpratik.founderguru.core.tag.Tagger
import io.github.ukpratik.founderguru.core.time.Clock
import io.github.ukpratik.founderguru.data.db.FgDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

enum class Tab { LOCAL, GLOBAL }

/** Row actions from the lists (US-009 save, D-017 visited state). */
interface StoryActions {
    suspend fun setSaved(id: Long, saved: Boolean)
    suspend fun markOpened(id: Long)
}

/** Read side of the story cache (LLD §3). Blending, sectioning and trending happen on these rows in `:core`. */
class StoryRepository(private val db: FgDatabase, private val catalog: SourcesCatalog, private val clock: Clock) : StoryActions {

    /**
     * The tab's 14-day window, newest first. Local = the region's rows blended with at most 30% via-Global rows
     * (US-002); Global = the Global sources' rows. The window start is taken when the flow is created.
     */
    fun tabWindow(tab: Tab, region: Region): Flow<List<StoryRow>> {
        val since = clock.now() - ItemNormaliser.WINDOW_MS
        val dao = db.stories()
        return when {
            tab == Tab.GLOBAL || region == Region.GLOBAL -> dao.regionWindow(Region.GLOBAL.name, since)
            else -> combine(dao.regionWindow(region.name, since), dao.viaGlobalCandidates(region.mentionBit, since)) { regional, via ->
                Blender.blend(regional, via)
            }
        }
    }

    /** Bookmark toggle (US-009); `savedAt` = [Clock.now] when saving. */
    override suspend fun setSaved(id: Long, saved: Boolean) = db.stories().setSavedAt(id, if (saved) clock.now() else null)

    /** Visited state (D-017). */
    override suspend fun markOpened(id: Long) = db.stories().setOpenedAt(id, clock.now())

    /**
     * Re-tags every stored story with the current rules (LLD §4.10). Called at start when the stored tagger version is
     * older than [Tagger.TAGGER_VERSION] (see [io.github.ukpratik.founderguru.AppContainer.startup]). Stories from sources
     * no longer in the catalogue keep their tags.
     */
    suspend fun retagAll(): Int = db.withTransaction {
        val sources = catalog.sources.associateBy { it.id }
        var n = 0
        for (row in db.stories().allForRetag()) {
            val source = sources[row.sourceId] ?: continue
            val tags = Tagger.tag(row.title, row.snippet, source)
            db.stories().updateTags(row.id, tags.lenses, tags.sectors, tags.regionMentions)
            n++
        }
        n
    }
}
