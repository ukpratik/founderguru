// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.settings

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.digest.DigestMode
import io.github.ukpratik.founderguru.core.model.FailureKind
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.SourceDef
import io.github.ukpratik.founderguru.data.prefs.ThemePref
import io.github.ukpratik.founderguru.ui.FakeRefresher
import io.github.ukpratik.founderguru.ui.indiaAndGlobal
import io.github.ukpratik.founderguru.ui.settingsStore
import io.github.ukpratik.founderguru.ui.source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Settings (US-010, US-012, D-017): digest permission paths, Off everywhere, region and source triggers, health. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private val catalog = (1..4).map { SourceDef("in$it", "India $it", Region.IN, "https://in$it.example/feed", "https://in$it.example") } +
        (1..6).map { SourceDef("gl$it", "Global $it", Region.GLOBAL, "https://gl$it.example/feed", "https://gl$it.example") } +
        SourceDef("gl7", "Show HN", Region.GLOBAL, "https://hnrss.org/show?points=50", "https://news.ycombinator.com")

    private class Harness(scope: TestScope, catalog: List<SourceDef>) {
        val settings = settingsStore(scope.backgroundScope)
        val states = MutableStateFlow(indiaAndGlobal())
        var notifications = true
        val refresher = FakeRefresher()
        val toggled = mutableListOf<Pair<String, Boolean>>()
        val vm = SettingsViewModel(settings, catalog, states, { notifications }, refresher, { id, on -> toggled += id to on })
    }

    @Test fun `digest - stored when allowed, permission asked on 13+, blocked otherwise`() = runTest(dispatcher) {
        val h = Harness(this, catalog)
        h.settings.completeOnboarding(Region.IN, DigestMode.OFF)
        assertThat(h.vm.chooseDigest(DigestMode.WEEKLY, canRequestPermission = false)).isEqualTo(DigestAsk.STORE)
        assertThat(h.settings.current().digest).isEqualTo(DigestMode.WEEKLY)

        h.notifications = false
        assertThat(h.vm.chooseDigest(DigestMode.DAILY, canRequestPermission = true)).isEqualTo(DigestAsk.REQUEST_PERMISSION)
        h.vm.onPermissionResult(DigestMode.DAILY, granted = false)
        assertThat(h.settings.current().digest).isEqualTo(DigestMode.OFF)
        assertThat(h.vm.uiState.filterNotNull().first().notificationsBlocked).isTrue()

        assertThat(h.vm.chooseDigest(DigestMode.WEEKLY, canRequestPermission = false)).isEqualTo(DigestAsk.BLOCKED)
        assertThat(h.settings.current().digest).isEqualTo(DigestMode.OFF)

        h.notifications = true
        h.vm.onPermissionResult(DigestMode.DAILY, granted = true)
        assertThat(h.settings.current().digest).isEqualTo(DigestMode.DAILY)
    }

    @Test fun `notifications blocked later turn the digest Off on resume`() = runTest(dispatcher) {
        val h = Harness(this, catalog)
        h.settings.completeOnboarding(Region.IN, DigestMode.WEEKLY)
        h.notifications = false
        h.vm.onResume()
        assertThat(h.settings.current().digest).isEqualTo(DigestMode.OFF)
    }

    @Test fun `theme, background refresh and region are stored, a new region refreshes`() = runTest(dispatcher) {
        val h = Harness(this, catalog)
        h.settings.completeOnboarding(Region.IN, DigestMode.OFF)
        h.vm.setTheme(ThemePref.DARK)
        h.vm.setBgRefresh(false)
        h.vm.setRegion(Region.IN) // unchanged: no refresh
        assertThat(h.refresher.calls).isEqualTo(0)
        h.vm.setRegion(Region.EU)
        val s = h.settings.current()
        assertThat(s.theme).isEqualTo(ThemePref.DARK)
        assertThat(s.bgRefresh).isFalse()
        assertThat(s.region).isEqualTo(Region.EU)
        assertThat(h.refresher.calls).isEqualTo(1)
    }

    @Test fun `a source switched on refreshes, off does not`() = runTest(dispatcher) {
        val h = Harness(this, catalog)
        h.vm.setSourceEnabled("in1", false)
        assertThat(h.refresher.calls).isEqualTo(0)
        h.vm.setSourceEnabled("in1", true)
        assertThat(h.toggled).containsExactly("in1" to false, "in1" to true).inOrder()
        assertThat(h.refresher.calls).isEqualTo(1)
    }

    @Test fun `summary and groups - region first, health words, all region sources off`() = runTest(dispatcher) {
        val h = Harness(this, catalog)
        h.settings.completeOnboarding(Region.IN, DigestMode.OFF)
        h.states.value = indiaAndGlobal().map {
            when (it.id) {
                "in2" -> it.copy(lastStatus = "TIMEOUT", newestItemAt = 5)
                "gl3" -> it.copy(lastStatus = "BLOCKED", lastHttpCode = 403)
                "gl4" -> it.copy(enabled = false)
                else -> it
            }
        }
        val summary = h.vm.uiState.filterNotNull().first { it.sources.failed == 1 }.sources
        assertThat(summary).isEqualTo(SourcesSummary(regionOn = 4, globalOn = 6, failed = 1, blocked = 1))

        val groups = h.vm.sources.filterNotNull().first { it.groups[1].on == 6 }.groups
        assertThat(groups.map { it.region }).containsExactly(Region.IN, Region.GLOBAL).inOrder()
        val items = groups.flatMap { it.items }.associateBy { it.id }
        assertThat(items.getValue("in2").health).isInstanceOf(Health.Failed::class.java)
        assertThat((items.getValue("in2").health as Health.Failed).kind).isEqualTo(FailureKind.TIMEOUT)
        assertThat((items.getValue("gl3").health as Health.Blocked).httpCode).isEqualTo(403)
        assertThat(items.getValue("gl4").health).isEqualTo(Health.Off)
        assertThat(items.getValue("gl7").viaHost).isEqualTo("hnrss.org")
        assertThat(items.getValue("in1").viaHost).isNull()

        h.states.value = h.states.value.map { if (it.region == "IN") it.copy(enabled = false) else it }
        assertThat(h.vm.sources.filterNotNull().first { it.allRegionOff != null }.allRegionOff).isEqualTo(Region.IN)
    }

    @Test fun `health mapping`() {
        assertThat(SourcesMapper.health(null)).isEqualTo(Health.Never)
        assertThat(SourcesMapper.health(source("a", Region.IN, status = null, attemptAt = null))).isEqualTo(Health.Never)
        assertThat(SourcesMapper.health(source("a", Region.IN, status = "NOT_MODIFIED", attemptAt = 7))).isEqualTo(Health.Ok(7))
        assertThat(SourcesMapper.health(source("a", Region.IN, enabled = false))).isEqualTo(Health.Off)
        assertThat(SourcesMapper.health(source("a", Region.IN, status = "PARSE", attemptAt = 9))).isEqualTo(Health.Failed(FailureKind.PARSE, null, 9, null))
        assertThat(SourcesMapper.hosts(catalog)).contains("hnrss.org")
        assertThat(SourcesMapper.hostOf("https://www.inc42.com/feed/")).isEqualTo("inc42.com")
    }
}
