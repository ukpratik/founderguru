// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.data.UserMessage
import io.github.ukpratik.founderguru.data.UserMessages
import io.github.ukpratik.founderguru.data.prefs.SettingsStore
import io.github.ukpratik.founderguru.data.prefs.ThemePref
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface AppUiState {
    /** DataStore not read yet: only the `bg` colour is drawn (LLD §5, about one frame). */
    data object Loading : AppUiState
    data object Onboarding : AppUiState

    /** Home. [globalOnly] = the user picked "Global only": no Local tab, three nav items (D-016). */
    data class Main(val globalOnly: Boolean) : AppUiState
}

/** Start destination and theme (LLD §5). */
class AppViewModel(settings: SettingsStore, private val messages: UserMessages) : ViewModel() {
    val uiState: StateFlow<AppUiState> = settings.settings
        .map { s -> if (!s.onboardingDone || s.region == null) AppUiState.Onboarding else AppUiState.Main(s.region == Region.GLOBAL) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppUiState.Loading)

    val theme: StateFlow<ThemePref> = settings.settings.map { it.theme }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemePref.SYSTEM)

    val message: StateFlow<UserMessage?> = messages.current

    fun messageShown(message: UserMessage) = messages.shown(message)
}
