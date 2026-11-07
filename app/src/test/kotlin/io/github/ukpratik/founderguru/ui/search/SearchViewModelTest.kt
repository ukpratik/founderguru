// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.search

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.model.StoryRow
import io.github.ukpratik.founderguru.ui.FakeActions
import io.github.ukpratik.founderguru.ui.FakeConnectivity
import io.github.ukpratik.founderguru.ui.story
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Search (US-014, LLD §5): 250 ms debounce, at least 2 characters, results / no results. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `debounced, minimum two characters, results and no results`() = runTest(dispatcher) {
        val asked = mutableListOf<String>()
        val search: (String) -> Flow<List<StoryRow>> = { q -> asked += q; flowOf(if (q == "upi") listOf(story(1)) else emptyList()) }
        val vm = SearchViewModel(search, FakeConnectivity(), FakeActions())
        vm.uiState.test {
            assertThat(awaitItem()).isEqualTo(SearchUiState.Idle)
            vm.onQuery("u")
            advanceTimeBy(300)
            vm.onQuery("up")
            vm.onQuery("upi ")
            advanceTimeBy(100)
            assertThat(asked).isEmpty() // still typing
            advanceTimeBy(300)
            assertThat(awaitItem()).isEqualTo(SearchUiState.Results("upi", listOf(story(1))))
            assertThat(asked).containsExactly("upi")

            vm.onQuery("zepto")
            advanceTimeBy(300)
            assertThat(awaitItem()).isEqualTo(SearchUiState.NoResults("zepto"))
            vm.clear()
            advanceTimeBy(300)
            assertThat(awaitItem()).isEqualTo(SearchUiState.Idle)
        }
    }
}
