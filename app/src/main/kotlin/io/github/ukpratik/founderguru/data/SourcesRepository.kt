// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.data

import androidx.room.withTransaction
import io.github.ukpratik.founderguru.core.model.SourcesCatalog
import io.github.ukpratik.founderguru.data.db.FgDatabase
import io.github.ukpratik.founderguru.data.db.SourceStateEntity

/** Keeps `source_state` in line with `assets/sources.json` (LLD §2.4). Runs at each app start, before the first refresh. */
class SourcesRepository(private val db: FgDatabase) {

    suspend fun sync(catalog: SourcesCatalog) = db.withTransaction {
        val dao = db.sources()
        val existing = dao.all().associateBy { it.id }
        for (source in catalog.sources) {
            val old = existing[source.id]
            val state = when {
                old == null -> SourceStateEntity(id = source.id, region = source.region.name, url = source.url)
                // A new asset URL: the old HTTP state belongs to the old URL (ETag, Last-Modified, permanent redirect).
                old.url != source.url -> old.copy(region = source.region.name, url = source.url, etag = null, lastModified = null, finalUrl = null)
                else -> old.copy(region = source.region.name)
            }
            if (state != old) dao.upsert(state)
        }
        val keep = catalog.sources.map { it.id }
        dao.deleteNotIn(keep)
        // Removed sources lose their unsaved stories; saved ones outlive them (D-017).
        db.stories().deleteUnsavedNotIn(keep)
    }
}
