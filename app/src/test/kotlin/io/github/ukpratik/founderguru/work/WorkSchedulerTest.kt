// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.digest.DigestMode
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** WorkManager wiring (LLD §4.11, work-testing): unique names, constraints, the digest delay and its target. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkSchedulerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private lateinit var wm: WorkManager
    private var now = ZonedDateTime.of(LocalDateTime.parse("2026-11-07T21:00"), zone).toInstant().toEpochMilli() // Saturday
    private lateinit var scheduler: WorkScheduler

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        wm = WorkManager.getInstance(context)
        scheduler = WorkScheduler({ wm }, { now }, { zone })
    }

    private fun infos(name: String): List<WorkInfo> = wm.getWorkInfosForUniqueWork(name).get()

    private fun pending(name: String) = infos(name).filter { !it.state.isFinished }

    @Test fun `background refresh is periodic 6 h with network and battery constraints, and cancels`() {
        scheduler.setBackgroundRefresh(true)
        val info = pending(WorkScheduler.REFRESH).single()
        assertThat(info.periodicityInfo!!.repeatIntervalMillis).isEqualTo(TimeUnit.HOURS.toMillis(6))
        assertThat(info.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
        assertThat(info.constraints.requiresBatteryNotLow()).isTrue()

        scheduler.setBackgroundRefresh(true) // UPDATE keeps one
        assertThat(pending(WorkScheduler.REFRESH)).hasSize(1)
        scheduler.setBackgroundRefresh(false)
        assertThat(pending(WorkScheduler.REFRESH)).isEmpty()
    }

    @Test fun `weekly digest waits until Monday 08 00 with no network constraint`() {
        scheduler.scheduleDigest(DigestMode.WEEKLY, keep = false)
        val info = pending(WorkScheduler.DIGEST).single()
        assertThat(info.initialDelayMillis).isEqualTo(TimeUnit.HOURS.toMillis(35)) // Sat 21:00 → Mon 08:00
        assertThat(info.constraints.requiredNetworkType).isEqualTo(NetworkType.NOT_REQUIRED)
    }

    @Test fun `changing the digest replaces, app start keeps, off cancels`() {
        scheduler.scheduleDigest(DigestMode.WEEKLY, keep = false)
        val weekly = pending(WorkScheduler.DIGEST).single().id
        scheduler.scheduleDigest(DigestMode.DAILY, keep = false)
        val daily = pending(WorkScheduler.DIGEST).single()
        assertThat(daily.id).isNotEqualTo(weekly)
        assertThat(daily.initialDelayMillis).isEqualTo(TimeUnit.HOURS.toMillis(11)) // Sat 21:00 → Sun 08:00

        now += TimeUnit.HOURS.toMillis(1)
        scheduler.scheduleDigest(DigestMode.DAILY, keep = true)
        assertThat(pending(WorkScheduler.DIGEST).single().id).isEqualTo(daily.id)

        scheduler.scheduleDigest(DigestMode.OFF, keep = false)
        assertThat(pending(WorkScheduler.DIGEST)).isEmpty()
    }

    @Test fun `the next run counts from the target`() {
        val target = ZonedDateTime.of(LocalDateTime.parse("2026-11-09T08:00"), zone)
        now = target.plusHours(3).toInstant().toEpochMilli() // ran 3 h late
        scheduler.scheduleNextDigest(target.toInstant().toEpochMilli(), DigestMode.WEEKLY)
        val info = pending(WorkScheduler.DIGEST).single()
        assertThat(info.initialDelayMillis).isEqualTo(TimeUnit.HOURS.toMillis(7 * 24 - 3))
    }
}
