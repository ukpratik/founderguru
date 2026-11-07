// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.work

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.ukpratik.founderguru.core.digest.DigestMode
import io.github.ukpratik.founderguru.core.digest.DigestSchedule
import io.github.ukpratik.founderguru.core.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** How the digest is (re)scheduled; an interface so [DigestRunner] is tested without WorkManager. */
interface DigestScheduling {
    /** Enqueues the run after the one targeted at [targetMs] (from the target, not from now: no drift). */
    fun scheduleNextDigest(targetMs: Long, mode: DigestMode)
}

/**
 * WorkManager wiring (LLD §4.11, US-008, US-012, NFR-005).
 * - Refresh: periodic 6 h, network connected and battery not low, unique `refresh`, policy UPDATE.
 * - Digest: one-time work to the next Monday / day 08:00 (DST-safe, [DigestSchedule]), unique `digest`, no network
 *   constraint (the digest still fires from the cache). Each run enqueues the next one.
 */
class WorkScheduler(
    private val workManager: () -> WorkManager,
    private val clock: Clock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : DigestScheduling {

    fun setBackgroundRefresh(on: Boolean) {
        val wm = workManager()
        if (!on) {
            wm.cancelUniqueWork(REFRESH)
            return
        }
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(REFRESH_HOURS, TimeUnit.HOURS).setConstraints(constraints).build()
        wm.enqueueUniquePeriodicWork(REFRESH, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * The digest was turned on or changed ([keep] = false: replace any pending run), or the app started ([keep] =
     * true: only enqueue when nothing is pending, LLD §4.11). [DigestMode.OFF] cancels.
     */
    fun scheduleDigest(mode: DigestMode, keep: Boolean) {
        if (mode == DigestMode.OFF) {
            workManager().cancelUniqueWork(DIGEST)
            return
        }
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(clock.now()), zone())
        enqueue(DigestSchedule.nextRun(now, mode), if (keep) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE)
    }

    /** Called by the running digest worker: appended after it, so the running work is not cancelled by its own successor. */
    override fun scheduleNextDigest(targetMs: Long, mode: DigestMode) {
        if (mode == DigestMode.OFF) return
        val z = zone()
        val target = ZonedDateTime.ofInstant(Instant.ofEpochMilli(targetMs), z)
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(clock.now()), z)
        enqueue(DigestSchedule.following(target, now, mode), ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueue(at: ZonedDateTime, policy: ExistingWorkPolicy) {
        val target = at.toInstant().toEpochMilli()
        val request = OneTimeWorkRequestBuilder<DigestWorker>()
            .setInitialDelay((target - clock.now()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(DigestWorker.KEY_TARGET to target))
            .build()
        workManager().enqueueUniqueWork(DIGEST, policy, request)
    }

    companion object {
        const val REFRESH = "refresh"
        const val DIGEST = "digest"
        const val REFRESH_HOURS = 6L
    }
}
