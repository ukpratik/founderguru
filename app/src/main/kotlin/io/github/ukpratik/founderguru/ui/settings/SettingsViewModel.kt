// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ukpratik.founderguru.core.digest.DigestMode
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.SourceDef
import io.github.ukpratik.founderguru.data.Refresher
import io.github.ukpratik.founderguru.data.db.SourceStateEntity
import io.github.ukpratik.founderguru.data.prefs.SettingsStore
import io.github.ukpratik.founderguru.data.prefs.ThemePref
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What a digest choice needs from the platform (Android 13+ permission). */
enum class DigestAsk { STORE, REQUEST_PERMISSION, BLOCKED }

/**
 * Settings (SCR-07..09, US-010, US-012, LLD §5). Every change is stored in DataStore; scheduling reacts to the stored
 * values ([io.github.ukpratik.founderguru.AppContainer]), so there is one source of truth.
 */
class SettingsViewModel(
    private val settings: SettingsStore,
    private val catalog: List<SourceDef>,
    states: Flow<List<SourceStateEntity>>,
    private val notificationsEnabled: () -> Boolean,
    private val refresher: Refresher,
    private val setSourceEnabled: suspend (String, Boolean) -> Unit,
) : ViewModel() {
    /** Bumped on resume and after a permission result, so a change made in system settings is picked up. */
    private val permissionCheck = MutableStateFlow(0)

    val uiState: StateFlow<SettingsUiState?> = combine(settings.settings, states, permissionCheck) { s, st, _ ->
        val region = s.region ?: Region.GLOBAL
        SettingsUiState(
            region = region,
            theme = s.theme,
            bgRefresh = s.bgRefresh,
            digest = s.digest,
            notificationsBlocked = !notificationsEnabled(),
            sources = SourcesMapper.summary(SourcesMapper.groups(catalog, st, region)),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sources: StateFlow<SourcesUiState?> = combine(settings.settings, states) { s, st ->
        val region = s.region ?: Region.GLOBAL
        SourcesUiState(SourcesMapper.groups(catalog, st, region), s.lastRefreshAt, region)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Resume: notifications blocked in system settings while the digest is on → Off everywhere (D-017). */
    fun onResume() {
        permissionCheck.value++
        viewModelScope.launch {
            if (settings.current().digest != DigestMode.OFF && !notificationsEnabled()) settings.setDigest(DigestMode.OFF)
        }
    }

    /**
     * A digest choice. Off is stored at once. Weekly/Daily are stored when notifications are allowed; on Android 13+
     * without the permission the UI asks first ([DigestAsk.REQUEST_PERMISSION], then [onPermissionResult]); otherwise
     * the choice stays Off and Settings explains why ([DigestAsk.BLOCKED]).
     */
    fun chooseDigest(mode: DigestMode, canRequestPermission: Boolean): DigestAsk {
        if (mode == DigestMode.OFF || notificationsEnabled()) {
            viewModelScope.launch { settings.setDigest(mode) }
            return DigestAsk.STORE
        }
        if (canRequestPermission) return DigestAsk.REQUEST_PERMISSION
        viewModelScope.launch { settings.setDigest(DigestMode.OFF) }
        permissionCheck.value++
        return DigestAsk.BLOCKED
    }

    fun onPermissionResult(mode: DigestMode, granted: Boolean) {
        permissionCheck.value++
        viewModelScope.launch { settings.setDigest(if (granted) mode else DigestMode.OFF) }
    }

    fun setTheme(theme: ThemePref) {
        viewModelScope.launch { settings.setTheme(theme) }
    }

    fun setBgRefresh(on: Boolean) {
        viewModelScope.launch { settings.setBgRefresh(on) }
    }

    /** Region redo (US-010): Local reloads for the new region; a refresh fetches its sources (LLD §4.1 step 6). */
    fun setRegion(region: Region) {
        viewModelScope.launch {
            if (settings.current().region == region) return@launch
            settings.setRegion(region)
            refresher.refresh()
        }
    }

    /** Turning a source off hides its stories at once (the window queries filter on `enabled`); on → refresh. */
    fun setSourceEnabled(id: String, on: Boolean) {
        viewModelScope.launch {
            setSourceEnabled.invoke(id, on)
            if (on) refresher.refresh()
        }
    }
}
