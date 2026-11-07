// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.work

import io.github.ukpratik.founderguru.core.digest.DigestContent
import io.github.ukpratik.founderguru.core.digest.DigestMode
import io.github.ukpratik.founderguru.core.digest.DigestSchedule
import io.github.ukpratik.founderguru.core.digest.DigestText
import io.github.ukpratik.founderguru.core.feed.Trending
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.StoryRow
import io.github.ukpratik.founderguru.core.time.Clock
import io.github.ukpratik.founderguru.data.Refresher
import io.github.ukpratik.founderguru.data.Tab
import io.github.ukpratik.founderguru.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Posts the digest notification (platform side, [io.github.ukpratik.founderguru.platform.DigestNotifier]). */
interface DigestPoster {
    /** `NotificationManagerCompat.areNotificationsEnabled()`. */
    fun canPost(): Boolean
    fun post(text: DigestText, tab: Tab)
}

/**
 * One digest run (US-012, LLD §4.11 `DigestWorker`), kept free of WorkManager so it is unit-tested:
 * 1. Digest off → nothing (the work was cancelled meanwhile). Notifications blocked → the digest is stored Off
 *    everywhere (D-017) and nothing is rescheduled.
 * 2. More than 24 h late → skipped (D-023 G-7), still rescheduled.
 * 3. Refresh first, with a 30 s budget; offline is fine (the digest uses the cache).
 * 4. n = the tab's rows first seen after its `last_visit` (Local, or Global for Global only), the number its
 *    divider will show; none → skipped. Workers never write `last_visit`.
 * 5. Every other outcome enqueues the next run from the target time.
 */
class DigestRunner(
    private val settings: SettingsStore,
    private val refresher: Refresher,
    private val window: (Tab, Region) -> Flow<List<StoryRow>>,
    private val poster: DigestPoster,
    private val scheduler: DigestScheduling,
    private val regionName: (Region) -> String?,
    private val clock: Clock,
) {
    enum class Outcome { OFF, BLOCKED, TOO_LATE, NO_VISIT, NOTHING_NEW, POSTED }

    suspend fun run(targetMs: Long): Outcome {
        val start = settings.current()
        val mode = if (start.onboardingDone) start.digest else DigestMode.OFF
        if (mode == DigestMode.OFF) return Outcome.OFF
        if (!poster.canPost()) {
            settings.setDigest(DigestMode.OFF)
            return Outcome.BLOCKED
        }
        try {
            if (DigestSchedule.isTooLate(targetMs, clock.now())) return Outcome.TOO_LATE
            withTimeoutOrNull(REFRESH_BUDGET_MS) { refresher.refreshNow() }
            val s = settings.current()
            val region = s.region ?: Region.GLOBAL
            val tab = if (region == Region.GLOBAL) Tab.GLOBAL else Tab.LOCAL
            val lastVisit = s.lastVisit(tab) ?: return Outcome.NO_VISIT
            val rows = window(tab, region).first()
            val text = DigestContent.build(regionName(region), mode, DigestContent.newSince(rows, lastVisit), Trending.compute(rows, clock.now()))
                ?: return Outcome.NOTHING_NEW
            poster.post(text, tab)
            return Outcome.POSTED
        } finally {
            scheduler.scheduleNextDigest(targetMs, mode)
        }
    }

    companion object {
        const val REFRESH_BUDGET_MS = 30_000L
    }
}
