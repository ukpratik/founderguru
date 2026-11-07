// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.onboarding

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.data.UserMessage
import io.github.ukpratik.founderguru.data.UserMessages
import io.github.ukpratik.founderguru.core.digest.DigestMode
import io.github.ukpratik.founderguru.ui.settingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Onboarding (US-001, US-012, D-017): suggestion, back, digest choice, permission denied → Off. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `region is pre-selected from the suggestion and back keeps the choice`() = runTest(dispatcher) {
        val vm = OnboardingViewModel(Region.IN, settingsStore(backgroundScope), UserMessages())
        assertThat(vm.uiState.value).isEqualTo(OnboardingUiState.RegionStep(Region.IN, Region.IN))
        vm.selectRegion(Region.SEA)
        vm.continueToDigest()
        assertThat(vm.uiState.value).isEqualTo(OnboardingUiState.DigestStep(Region.IN, Region.SEA, DigestMode.WEEKLY))
        assertThat(vm.back()).isTrue()
        assertThat(vm.uiState.value).isEqualTo(OnboardingUiState.RegionStep(Region.IN, Region.SEA))
        assertThat(vm.back()).isFalse()
    }

    @Test fun `weekly with permission is stored`() = runTest(dispatcher) {
        val settings = settingsStore(backgroundScope)
        val messages = UserMessages()
        val vm = OnboardingViewModel(Region.EU, settings, messages)
        vm.continueToDigest()
        vm.finish(notificationsAllowed = true)
        val s = settings.settings.first { it.onboardingDone }
        assertThat(s.region).isEqualTo(Region.EU)
        assertThat(s.digest).isEqualTo(DigestMode.WEEKLY)
        assertThat(messages.current.value).isNull()
        assertThat(vm.uiState.value).isEqualTo(OnboardingUiState.Done)
    }

    @Test fun `denied permission stores Off and leaves a snackbar for Home`() = runTest(dispatcher) {
        val settings = settingsStore(backgroundScope)
        val messages = UserMessages()
        val vm = OnboardingViewModel(Region.GLOBAL, settings, messages)
        vm.continueToDigest()
        vm.selectDigest(DigestMode.DAILY)
        vm.finish(notificationsAllowed = false)
        val s = settings.settings.first { it.onboardingDone }
        assertThat(s.digest).isEqualTo(DigestMode.OFF)
        assertThat(s.region).isEqualTo(Region.GLOBAL)
        assertThat(messages.current.value).isEqualTo(UserMessage.DIGEST_OFF)
        messages.shown(UserMessage.DIGEST_OFF)
        assertThat(messages.current.value).isNull()
    }

    @Test fun `not now asks nothing and shows no message`() = runTest(dispatcher) {
        val settings = settingsStore(backgroundScope)
        val messages = UserMessages()
        val vm = OnboardingViewModel(Region.IN, settings, messages)
        vm.continueToDigest()
        vm.selectDigest(DigestMode.OFF)
        vm.finish(notificationsAllowed = false)
        assertThat(settings.settings.first { it.onboardingDone }.digest).isEqualTo(DigestMode.OFF)
        assertThat(messages.current.value).isNull()
    }
}
