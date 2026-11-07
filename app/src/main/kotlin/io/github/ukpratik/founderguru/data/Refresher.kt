// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.data

import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.time.Clock
import io.github.ukpratik.founderguru.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Starts refreshes from the UI (first sync, Retry, pull to refresh) and tells the feed whether one is running. */
interface Refresher {
    val refreshing: StateFlow<Boolean>

    /** Fire and forget: runs in the app scope, so leaving the screen never cancels a refresh. A second call joins. */
    fun refresh()
}

/**
 * The M3 refresh trigger (LLD §4.1): refreshes the chosen region plus Global and records `last_refresh_at` when at
 * least one source succeeded. Waits for [startup] (catalogue sync) first. Background and automatic triggers are M4.
 */
class AppRefresher(
    private val repository: RefreshRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val startup: Job,
) : Refresher {
    private val _refreshing = MutableStateFlow(false)
    override val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    private var running: Job? = null

    override fun refresh() {
        if (running?.isActive == true) return
        _refreshing.value = true
        running = scope.launch {
            try {
                startup.join()
                val region = settings.current().region ?: Region.GLOBAL
                val result = repository.refresh(region)
                if (result.anySuccess) settings.setLastRefreshAt(clock.now())
            } finally {
                _refreshing.value = false
            }
        }
    }
}
