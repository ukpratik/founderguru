// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.nav

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.FounderGuruApp
import io.github.ukpratik.founderguru.MainActivity
import io.github.ukpratik.founderguru.core.digest.DigestMode
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.data.Tab
import io.github.ukpratik.founderguru.data.Visit
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper

/**
 * BUG-001 (US-008): the last-visit time T moves only when the user leaves the tab or the app really stops. Recreating
 * the activity for a configuration change (rotation, dark mode, font scale, locale) must keep the visit and T.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VisitLifecycleTest {
    private val app: FounderGuruApp get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        runBlocking {
            app.container.settings.completeOnboarding(Region.IN, DigestMode.OFF)
            app.container.settings.setLastVisit(Tab.LOCAL, LAST_VISIT)
        }
    }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "timed out" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private fun lastVisitLocal() = runBlocking { app.container.settings.current().lastVisitLocal }

    @Test fun `configuration change keeps the visit, a real stop ends it`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor { app.container.visits.visit.value?.tab == Tab.LOCAL }
            val visit: Visit = app.container.visits.visit.value!!
            assertThat(visit.anchor).isEqualTo(LAST_VISIT)

            scenario.recreate() // what rotation, dark mode, font scale and locale changes do
            repeat(20) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(10) }
            assertThat(app.container.visits.visit.value).isEqualTo(visit)
            assertThat(lastVisitLocal()).isEqualTo(LAST_VISIT)

            scenario.moveToState(Lifecycle.State.CREATED) // the app goes to the background
            waitFor { app.container.visits.visit.value == null }
            assertThat(lastVisitLocal()).isGreaterThan(LAST_VISIT)
        }
    }

    private companion object {
        const val LAST_VISIT = 1_000L
    }
}
