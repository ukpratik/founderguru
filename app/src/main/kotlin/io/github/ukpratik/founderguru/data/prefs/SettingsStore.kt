// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.ukpratik.founderguru.core.feed.LensFilter
import io.github.ukpratik.founderguru.core.model.Lens
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.data.Tab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemePref { SYSTEM, LIGHT, DARK }

enum class DigestMode { OFF, WEEKLY, DAILY }

/** A snapshot of `settings.preferences_pb` (LLD §2.2). */
data class Settings(
    val onboardingDone: Boolean = false,
    val region: Region? = null,
    val theme: ThemePref = ThemePref.SYSTEM,
    val bgRefresh: Boolean = true,
    val digest: DigestMode = DigestMode.OFF,
    val lensLocal: LensFilter = LensFilter.All,
    val lensGlobal: LensFilter = LensFilter.All,
    val lastVisitLocal: Long? = null,
    val lastVisitGlobal: Long? = null,
    val lastRefreshAt: Long? = null,
    val limitedBannerDismissed: Set<Region> = emptySet(),
    val failedNoteDismissedAt: Long = 0,
    val taggerVersion: Int = 0,
) {
    fun lens(tab: Tab): LensFilter = if (tab == Tab.LOCAL) lensLocal else lensGlobal
    fun lastVisit(tab: Tab): Long? = if (tab == Tab.LOCAL) lastVisitLocal else lastVisitGlobal
}

/** DataStore Preferences behind typed accessors (LLD §2.2). Unknown stored values fall back to the defaults. */
class SettingsStore(private val store: DataStore<Preferences>) {

    val settings: Flow<Settings> = store.data.map(::read).distinctUntilChanged()

    suspend fun current(): Settings = settings.first()

    suspend fun completeOnboarding(region: Region, digest: DigestMode) = store.edit {
        it[REGION] = region.name
        it[DIGEST] = digest.name
        it[ONBOARDING_DONE] = true
    }

    suspend fun setRegion(region: Region) = store.edit { it[REGION] = region.name }
    suspend fun setDigest(mode: DigestMode) = store.edit { it[DIGEST] = mode.name }
    suspend fun setTheme(theme: ThemePref) = store.edit { it[THEME] = theme.name }

    suspend fun setLens(tab: Tab, lens: LensFilter) = store.edit {
        it[if (tab == Tab.LOCAL) LENS_LOCAL else LENS_GLOBAL] = when (lens) {
            LensFilter.All -> ALL
            is LensFilter.Only -> lens.lens.name
        }
    }

    suspend fun setLastVisit(tab: Tab, at: Long) = store.edit { it[if (tab == Tab.LOCAL) LAST_VISIT_LOCAL else LAST_VISIT_GLOBAL] = at }
    suspend fun setLastRefreshAt(at: Long) = store.edit { it[LAST_REFRESH_AT] = at }
    suspend fun dismissLimitedBanner(region: Region) = store.edit { it[LIMITED_DISMISSED] = it[LIMITED_DISMISSED].orEmpty() + region.name }
    suspend fun dismissFailedNote(at: Long) = store.edit { it[FAILED_DISMISSED_AT] = at }
    suspend fun setTaggerVersion(version: Int) = store.edit { it[TAGGER_VERSION] = version }

    private fun read(p: Preferences) = Settings(
        onboardingDone = p[ONBOARDING_DONE] ?: false,
        region = p[REGION]?.let { enumOrNull<Region>(it) },
        theme = p[THEME]?.let { enumOrNull<ThemePref>(it) } ?: ThemePref.SYSTEM,
        bgRefresh = p[BG_REFRESH] ?: true,
        digest = p[DIGEST]?.let { enumOrNull<DigestMode>(it) } ?: DigestMode.OFF,
        lensLocal = lensOf(p[LENS_LOCAL]),
        lensGlobal = lensOf(p[LENS_GLOBAL]),
        lastVisitLocal = p[LAST_VISIT_LOCAL],
        lastVisitGlobal = p[LAST_VISIT_GLOBAL],
        lastRefreshAt = p[LAST_REFRESH_AT],
        limitedBannerDismissed = p[LIMITED_DISMISSED].orEmpty().mapNotNull { enumOrNull<Region>(it) }.toSet(),
        failedNoteDismissedAt = p[FAILED_DISMISSED_AT] ?: 0,
        taggerVersion = p[TAGGER_VERSION] ?: 0,
    )

    private fun lensOf(value: String?): LensFilter = value?.let { enumOrNull<Lens>(it) }?.let(LensFilter::Only) ?: LensFilter.All

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? = enumValues<E>().firstOrNull { it.name == name }

    companion object {
        const val FILE = "settings"
        private const val ALL = "ALL"
        private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val REGION = stringPreferencesKey("region")
        private val THEME = stringPreferencesKey("theme")
        private val BG_REFRESH = booleanPreferencesKey("bg_refresh")
        private val DIGEST = stringPreferencesKey("digest")
        private val LENS_LOCAL = stringPreferencesKey("lens_local")
        private val LENS_GLOBAL = stringPreferencesKey("lens_global")
        private val LAST_VISIT_LOCAL = longPreferencesKey("last_visit_local")
        private val LAST_VISIT_GLOBAL = longPreferencesKey("last_visit_global")
        private val LAST_REFRESH_AT = longPreferencesKey("last_refresh_at")
        private val LIMITED_DISMISSED = stringSetPreferencesKey("limited_banner_dismissed")
        private val FAILED_DISMISSED_AT = longPreferencesKey("failed_note_dismissed_at")
        private val TAGGER_VERSION = intPreferencesKey("tagger_version")
    }
}
