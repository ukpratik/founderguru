// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.data

import io.github.ukpratik.founderguru.core.time.Clock
import io.github.ukpratik.founderguru.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One visit to a feed tab: [anchor] is the frozen `last_visit_<tab>` read when the visit began (null = first visit). */
data class Visit(val tab: Tab, val anchor: Long?, val seq: Int)

/**
 * The last-visit lifecycle (US-008, LLD §4.9). The UI reports which feed tab is in front ([enter]) and when none is
 * (another top-level destination, or the app stopped: [leave]). Leaving writes `last_visit_<tab> = now`; entering
 * reads the stored value once and freezes it as the anchor, so refreshes during the visit never move the divider.
 * Child screens of a tab (What's hot, Search) keep the visit. Workers never write it.
 */
class VisitTracker(private val settings: SettingsStore, private val clock: Clock, private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private val _visit = MutableStateFlow<Visit?>(null)
    val visit: StateFlow<Visit?> = _visit.asStateFlow()
    private var seq = 0

    fun enter(tab: Tab) {
        scope.launch {
            mutex.withLock {
                val current = _visit.value
                if (current?.tab == tab) return@withLock
                if (current != null) settings.setLastVisit(current.tab, clock.now())
                _visit.value = Visit(tab, settings.current().lastVisit(tab), ++seq)
            }
        }
    }

    fun leave() {
        scope.launch {
            mutex.withLock {
                val current = _visit.value ?: return@withLock
                settings.setLastVisit(current.tab, clock.now())
                _visit.value = null
            }
        }
    }
}
