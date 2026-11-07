// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.data

import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.time.Clock
import io.github.ukpratik.founderguru.data.prefs.SettingsStore
import io.github.ukpratik.founderguru.platform.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/** Starts refreshes (LLD §4.1 step 6) and tells the feed whether one is running. */
interface Refresher {
    val refreshing: StateFlow<Boolean>

    /** Fire and forget from the UI: runs in the app scope, so leaving the screen never cancels a refresh. */
    fun refresh()

    /**
     * Refreshes now and returns the result; null when the device is offline (nothing is fetched, so source health is
     * not marked failed, LLD §4.1 step 1). A caller arriving during a run joins it. Used by the workers.
     */
    suspend fun refreshNow(): RefreshResult?
}

/**
 * Refreshes the chosen region plus Global and records `last_refresh_at` when at least one source succeeded. Waits for
 * [startup] (catalogue sync) first. Triggers: first sync, Retry, pull to refresh, app open with a cache older than 1 h
 * and reconnect ([ForegroundRefresh]), region change, a source switched back on, and the two workers.
 */
class AppRefresher(
    private val repository: RefreshRepository,
    private val settings: SettingsStore,
    private val connectivity: Connectivity,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val startup: Job,
) : Refresher {
    private val _refreshing = MutableStateFlow(false)
    override val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private val active = AtomicInteger(0)

    override fun refresh() {
        scope.launch { refreshNow() }
    }

    override suspend fun refreshNow(): RefreshResult? {
        if (!connectivity.isOnline.value) return null
        active.incrementAndGet()
        _refreshing.value = true
        try {
            startup.join()
            val region = settings.current().region ?: Region.GLOBAL
            val result = repository.refresh(region)
            if (result.anySuccess) settings.setLastRefreshAt(clock.now())
            return result
        } finally {
            if (active.decrementAndGet() == 0) _refreshing.value = false
        }
    }
}
