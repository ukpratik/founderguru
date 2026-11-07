// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.data.UserMessage
import io.github.ukpratik.founderguru.data.UserMessages
import io.github.ukpratik.founderguru.data.prefs.DigestMode
import io.github.ukpratik.founderguru.data.prefs.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface OnboardingUiState {
    /** Step 1 (SCR-01): [suggested] gets the "Suggested" tag and is pre-selected (US-001). */
    data class RegionStep(val suggested: Region, val selected: Region) : OnboardingUiState

    /** Step 2 (SCR-02): Weekly is pre-selected (US-012). */
    data class DigestStep(val suggested: Region, val region: Region, val choice: DigestMode) : OnboardingUiState

    data object Done : OnboardingUiState
}

/**
 * Onboarding (US-001, US-012). The digest choice is stored here; scheduling it is M4. On Android 13+ the UI asks for
 * POST_NOTIFICATIONS only after Weekly or Daily, and a denial stores Off (D-017) and leaves a snackbar for Home.
 */
class OnboardingViewModel(
    suggested: Region,
    private val settings: SettingsStore,
    private val messages: UserMessages,
) : ViewModel(), OnboardingActions {
    private val _state = MutableStateFlow<OnboardingUiState>(OnboardingUiState.RegionStep(suggested, suggested))
    val uiState: StateFlow<OnboardingUiState> = _state.asStateFlow()

    override fun selectRegion(region: Region) = _state.update { if (it is OnboardingUiState.RegionStep) it.copy(selected = region) else it }

    override fun continueToDigest() = _state.update {
        if (it is OnboardingUiState.RegionStep) OnboardingUiState.DigestStep(it.suggested, it.selected, DigestMode.WEEKLY) else it
    }

    override fun selectDigest(mode: DigestMode) = _state.update { if (it is OnboardingUiState.DigestStep) it.copy(choice = mode) else it }

    /** System back: step 2 returns to step 1 with the choice kept. Returns false on step 1 (the activity finishes). */
    override fun back(): Boolean {
        val s = _state.value
        if (s !is OnboardingUiState.DigestStep) return false
        _state.value = OnboardingUiState.RegionStep(s.suggested, s.region)
        return true
    }

    /** [notificationsAllowed] = false when the permission was denied; true when granted or not needed (Android < 13). */
    override fun finish(notificationsAllowed: Boolean) {
        val s = _state.value as? OnboardingUiState.DigestStep ?: return
        val digest = if (notificationsAllowed) s.choice else DigestMode.OFF
        viewModelScope.launch {
            settings.completeOnboarding(s.region, digest)
            if (s.choice != DigestMode.OFF && !notificationsAllowed) messages.show(UserMessage.DIGEST_OFF)
            _state.value = OnboardingUiState.Done
        }
    }
}
