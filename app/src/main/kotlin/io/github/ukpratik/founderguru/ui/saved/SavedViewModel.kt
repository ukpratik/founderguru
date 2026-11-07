// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ukpratik.founderguru.core.model.StoryRow
import io.github.ukpratik.founderguru.data.StoryActions
import io.github.ukpratik.founderguru.platform.Connectivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Saved (SCR-06, US-009, LLD §5): always renders from the device, so offline only adds the strip. */
sealed interface SavedUiState {
    data object Loading : SavedUiState
    data class Empty(val offline: Boolean) : SavedUiState
    data class Content(val rows: List<StoryRow>, val offline: Boolean) : SavedUiState
}

class SavedViewModel(saved: Flow<List<StoryRow>>, connectivity: Connectivity, private val actions: StoryActions) : ViewModel() {
    val uiState: StateFlow<SavedUiState> = combine(saved, connectivity.isOnline) { rows, online ->
        if (rows.isEmpty()) SavedUiState.Empty(!online) else SavedUiState.Content(rows, !online)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedUiState.Loading)

    fun setSaved(row: StoryRow, saved: Boolean) {
        viewModelScope.launch { actions.setSaved(row.id, saved) }
    }

    fun markOpened(row: StoryRow) {
        viewModelScope.launch { actions.markOpened(row.id) }
    }
}
