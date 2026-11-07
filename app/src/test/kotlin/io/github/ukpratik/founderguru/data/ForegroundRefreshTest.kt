// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.data

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.digest.DigestMode
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.ui.FakeConnectivity
import io.github.ukpratik.founderguru.ui.FakeRefresher
import io.github.ukpratik.founderguru.ui.HOUR
import io.github.ukpratik.founderguru.ui.NOW
import io.github.ukpratik.founderguru.ui.settingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Foreground triggers (US-008, D-022 OQ-A, D-017): open with a cache older than 1 h, and reconnect while visible. */
@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundRefreshTest {
    private class Harness(scope: TestScope, online: Boolean = true) {
        val settings = settingsStore(scope.backgroundScope)
        val connectivity = FakeConnectivity(online)
        val refresher = FakeRefresher()
        var now = NOW
        val trigger = ForegroundRefresh(settings, connectivity, refresher, { now }, scope.backgroundScope)
    }

    private suspend fun Harness.onboarded(lastRefresh: Long?) {
        settings.completeOnboarding(Region.IN, DigestMode.OFF)
        lastRefresh?.let { settings.setLastRefreshAt(it) }
    }

    @Test fun `opening with a cache older than 1 h refreshes once`() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness(this)
        h.onboarded(NOW - HOUR - 1)
        h.trigger.onStart()
        runCurrent()
        assertThat(h.refresher.calls).isEqualTo(1)
    }

    @Test fun `a fresh cache, offline, or before the first sync does not refresh on open`() = runTest(UnconfinedTestDispatcher()) {
        val fresh = Harness(this)
        fresh.onboarded(NOW - HOUR)
        fresh.trigger.onStart()
        val offline = Harness(this, online = false)
        offline.onboarded(NOW - 5 * HOUR)
        offline.trigger.onStart()
        val first = Harness(this)
        first.onboarded(null)
        first.trigger.onStart()
        runCurrent()
        assertThat(fresh.refresher.calls + offline.refresher.calls + first.refresher.calls).isEqualTo(0)
    }

    @Test fun `the connection returning in the foreground refreshes, in the background it does not`() = runTest(UnconfinedTestDispatcher()) {
        val h = Harness(this, online = false)
        h.onboarded(NOW - 10)
        h.trigger.onStart()
        runCurrent()
        h.connectivity.isOnline.value = true
        runCurrent()
        assertThat(h.refresher.calls).isEqualTo(1)
        h.connectivity.isOnline.value = false
        h.connectivity.isOnline.value = true
        runCurrent()
        assertThat(h.refresher.calls).isEqualTo(2)

        h.trigger.onStop()
        h.connectivity.isOnline.value = false
        h.connectivity.isOnline.value = true
        runCurrent()
        assertThat(h.refresher.calls).isEqualTo(2)
    }
}
