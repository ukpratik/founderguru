// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.digest.DigestMode
import io.github.ukpratik.founderguru.core.feed.LensFilter
import io.github.ukpratik.founderguru.core.model.Lens
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.data.prefs.Settings
import io.github.ukpratik.founderguru.data.prefs.SettingsStore
import io.github.ukpratik.founderguru.data.prefs.ThemePref
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Settings persistence (LLD §2.2, US-010): defaults, a round trip through the file, unknown values fall back. */
class SettingsStoreTest {
    private val dir: File = Files.createTempDirectory("fg-settings-test").toFile()

    private fun open(scope: CoroutineScope) =
        PreferenceDataStoreFactory.create(scope = scope) { File(dir, "settings.preferences_pb") }

    @Test fun `defaults before anything is stored`() = runTest {
        val store = SettingsStore(open(backgroundScope))
        assertThat(store.current()).isEqualTo(Settings())
        assertThat(store.current().bgRefresh).isTrue()
        assertThat(store.current().digest).isEqualTo(DigestMode.OFF)
        assertThat(store.current().theme).isEqualTo(ThemePref.SYSTEM)
    }

    @Test fun `every setting survives reopening the file`() = runTest {
        val job = Job()
        val first = SettingsStore(open(CoroutineScope(coroutineContext + job)))
        first.completeOnboarding(Region.EU, DigestMode.WEEKLY)
        first.setRegion(Region.AF)
        first.setDigest(DigestMode.DAILY)
        first.setTheme(ThemePref.DARK)
        first.setBgRefresh(false)
        first.setLens(Tab.LOCAL, LensFilter.Only(Lens.PLAYBOOKS))
        first.setLens(Tab.GLOBAL, LensFilter.Only(Lens.FUNDING))
        first.setLastVisit(Tab.LOCAL, 11)
        first.setLastVisit(Tab.GLOBAL, 22)
        first.setLastRefreshAt(33)
        first.dismissLimitedBanner(Region.SEA)
        first.dismissFailedNote(44)
        first.setTaggerVersion(2)
        first.setFirstSaveHintShown()
        job.cancelAndJoin()

        val second = SettingsStore(open(backgroundScope)).current()
        assertThat(second).isEqualTo(
            Settings(
                onboardingDone = true, region = Region.AF, theme = ThemePref.DARK, bgRefresh = false, digest = DigestMode.DAILY,
                lensLocal = LensFilter.Only(Lens.PLAYBOOKS), lensGlobal = LensFilter.Only(Lens.FUNDING), lastVisitLocal = 11,
                lastVisitGlobal = 22, lastRefreshAt = 33, limitedBannerDismissed = setOf(Region.SEA), failedNoteDismissedAt = 44,
                taggerVersion = 2, firstSaveHintShown = true,
            ),
        )
    }

    @Test fun `unknown stored values fall back to the defaults`() = runTest {
        val raw = open(backgroundScope)
        raw.edit {
            it[stringPreferencesKey("theme")] = "SEPIA"
            it[stringPreferencesKey("digest")] = "HOURLY"
            it[stringPreferencesKey("region")] = "MARS"
            it[stringPreferencesKey("lens_local")] = "GOSSIP"
        }
        val s = SettingsStore(raw).current()
        assertThat(s.theme).isEqualTo(ThemePref.SYSTEM)
        assertThat(s.digest).isEqualTo(DigestMode.OFF)
        assertThat(s.region).isNull()
        assertThat(s.lensLocal).isEqualTo(LensFilter.All)
    }
}
