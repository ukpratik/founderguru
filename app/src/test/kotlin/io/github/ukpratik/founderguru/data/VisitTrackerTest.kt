// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.data

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.ui.settingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Last-visit lifecycle (US-008, LLD §4.9): leaving writes now, entering freezes the stored value. */
@OptIn(ExperimentalCoroutinesApi::class)
class VisitTrackerTest {
    @Test fun `enter freezes the stored last visit and leave writes now`() = runTest {
        val settings = settingsStore(backgroundScope)
        var now = 1_000L
        val tracker = VisitTracker(settings, { now }, backgroundScope)

        tracker.enter(Tab.LOCAL)
        assertThat(tracker.visit.filterNotNull().first()).isEqualTo(Visit(Tab.LOCAL, anchor = null, seq = 1))

        now = 2_000L
        tracker.enter(Tab.GLOBAL) // switching tabs ends the Local visit
        assertThat(tracker.visit.filterNotNull().first { it.tab == Tab.GLOBAL }.anchor).isNull()
        assertThat(settings.current().lastVisitLocal).isEqualTo(2_000L)

        now = 3_000L
        tracker.leave() // app stopped
        assertThat(tracker.visit.first { it == null }).isNull()
        assertThat(settings.current().lastVisitGlobal).isEqualTo(3_000L)

        tracker.enter(Tab.LOCAL)
        assertThat(tracker.visit.filterNotNull().first().anchor).isEqualTo(2_000L)
        tracker.enter(Tab.LOCAL) // same tab again: no new visit
        assertThat(tracker.visit.value?.seq).isEqualTo(3)
    }
}
