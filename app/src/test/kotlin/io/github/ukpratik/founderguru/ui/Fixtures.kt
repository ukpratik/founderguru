// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.ukpratik.founderguru.core.model.Lens
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.StoryRow
import io.github.ukpratik.founderguru.core.tag.Sector
import io.github.ukpratik.founderguru.data.RefreshResult
import io.github.ukpratik.founderguru.data.Refresher
import io.github.ukpratik.founderguru.data.StoryActions
import io.github.ukpratik.founderguru.data.db.SourceStateEntity
import io.github.ukpratik.founderguru.data.prefs.SettingsStore
import io.github.ukpratik.founderguru.platform.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.nio.file.Files

const val HOUR = 60L * 60 * 1000
const val NOW = 1_000L * 24 * HOUR

fun story(
    id: Long,
    source: String = "inc42",
    publishedAgo: Long = HOUR,
    firstSeenAgo: Long = publishedAgo,
    lenses: Set<Lens> = emptySet(),
    sectors: Set<Sector> = emptySet(),
    image: String? = null,
    via: Boolean = false,
    saved: Boolean = false,
    opened: Boolean = false,
    title: String = "Story $id",
) = StoryRow(
    id = id, sourceId = source, sourceName = source.replaceFirstChar { it.uppercase() }, url = "https://example.com/$id",
    title = title, snippet = "Snippet $id", imageUrl = image, publishedAt = NOW - publishedAgo, firstSeenAt = NOW - firstSeenAgo,
    lenses = lenses.fold(0) { a, l -> a or l.bit }, sectors = sectors.fold(0L) { a, s -> a or s.bit }, regionMentions = 0,
    savedAt = if (saved) NOW else null, openedAt = if (opened) NOW else null, viaGlobal = via,
)

fun source(id: String, region: Region, enabled: Boolean = true, status: String? = "OK", attemptAt: Long? = NOW - HOUR) =
    SourceStateEntity(id = id, region = region.name, url = "https://$id.example/feed", enabled = enabled, lastStatus = status, lastAttemptAt = attemptAt)

/** India: 4 sources; Global: 7 sources (as in the shipped catalogue). */
fun indiaAndGlobal(): List<SourceStateEntity> =
    (1..4).map { source("in$it", Region.IN) } + (1..7).map { source("gl$it", Region.GLOBAL) }

fun settingsStore(scope: CoroutineScope): SettingsStore {
    val dir: File = Files.createTempDirectory("fg-settings").toFile()
    return SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") })
}

class FakeConnectivity(online: Boolean = true) : Connectivity {
    override val isOnline = MutableStateFlow(online)
}

class FakeRefresher(var result: RefreshResult? = RefreshResult(emptyList(), emptyList(), emptyMap())) : Refresher {
    override val refreshing = MutableStateFlow(false)
    var calls = 0
    var nowCalls = 0
    override fun refresh() {
        calls++
    }
    override suspend fun refreshNow(): RefreshResult? {
        nowCalls++
        return result
    }
}

class FakeActions : StoryActions {
    val saved = mutableMapOf<Long, Boolean>()
    val opened = mutableListOf<Long>()
    override suspend fun setSaved(id: Long, saved: Boolean) {
        this.saved[id] = saved
    }
    override suspend fun markOpened(id: Long) {
        opened += id
    }
    override suspend fun restoreSavedAt(id: Long, savedAt: Long) {
        saved[id] = true
    }
}
