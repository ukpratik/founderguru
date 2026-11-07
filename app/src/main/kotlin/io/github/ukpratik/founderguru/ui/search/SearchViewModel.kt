// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ukpratik.founderguru.core.model.StoryRow
import io.github.ukpratik.founderguru.data.StoryActions
import io.github.ukpratik.founderguru.platform.Connectivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Search (SCR-10, US-014, LLD §5). No loading state: the query is local and fast. */
sealed interface SearchUiState {
    data object Idle : SearchUiState
    data class Results(val q: String, val rows: List<StoryRow>) : SearchUiState
    data class NoResults(val q: String) : SearchUiState
}

/** Debounces input by 250 ms and needs at least 2 characters; works offline. Nothing is stored (no recent searches). */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(search: (String) -> Flow<List<StoryRow>>, connectivity: Connectivity, private val actions: StoryActions) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val uiState: StateFlow<SearchUiState> = _query
        .debounce(DEBOUNCE_MS)
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.length < MIN_CHARS) {
                flowOf(SearchUiState.Idle)
            } else {
                search(q).map { rows -> if (rows.isEmpty()) SearchUiState.NoResults(q) else SearchUiState.Results(q, rows) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState.Idle)

    val offline: StateFlow<Boolean> = connectivity.isOnline.map { !it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onQuery(q: String) {
        _query.value = q
    }

    fun clear() {
        _query.value = ""
    }

    fun setSaved(row: StoryRow, saved: Boolean) {
        viewModelScope.launch { actions.setSaved(row.id, saved) }
    }

    fun markOpened(row: StoryRow) {
        viewModelScope.launch { actions.markOpened(row.id) }
    }

    companion object {
        const val DEBOUNCE_MS = 250L
        const val MIN_CHARS = 2
    }
}
