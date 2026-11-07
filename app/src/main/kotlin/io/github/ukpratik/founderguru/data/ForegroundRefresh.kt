// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.data

import io.github.ukpratik.founderguru.core.time.Clock
import io.github.ukpratik.founderguru.data.prefs.Settings
import io.github.ukpratik.founderguru.data.prefs.SettingsStore
import io.github.ukpratik.founderguru.platform.Connectivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The foreground refresh triggers (US-008, D-022 OQ-A, D-017): one automatic refresh when the app comes to the
 * foreground with a cache more than 1 h old, and one when the connection returns while the app is in the foreground.
 * Both only after the first sync succeeded (before that, the feed's first-sync trigger owns refreshing).
 */
class ForegroundRefresh(
    private val settings: SettingsStore,
    private val connectivity: Connectivity,
    private val refresher: Refresher,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    /** The activity started (it is in the foreground). */
    fun onStart() {
        job?.cancel()
        job = scope.launch {
            val s = settings.current()
            val last = s.lastRefreshAt
            if (ready(s) && last != null && clock.now() - last > STALE_MS && connectivity.isOnline.value) refresher.refresh()
            var wasOnline = connectivity.isOnline.value
            connectivity.isOnline.collect { online ->
                if (online && !wasOnline && ready(settings.current())) refresher.refresh()
                wasOnline = online
            }
        }
    }

    /** The activity stopped: no reconnect refresh in the background (that is the worker's job). */
    fun onStop() {
        job?.cancel()
        job = null
    }

    private fun ready(s: Settings) = s.onboardingDone && s.lastRefreshAt != null

    companion object {
        /** D-022 OQ-A: refresh on open when the cache is more than 1 h old. */
        const val STALE_MS = 60L * 60 * 1000
    }
}
