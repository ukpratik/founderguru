package io.github.ukpratik.founderguru.ui.onboarding

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.digest.DigestMode
import io.github.ukpratik.founderguru.ui.theme.FounderGuruTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** BUG-011: only Android 13+ shows a notification permission prompt, so only there may the hint promise one. */
@RunWith(RobolectricTestRunner::class)
class DigestRationaleTest {
    @get:Rule val rule = createComposeRule()

    private val noop = object : OnboardingActions {
        override fun selectRegion(region: Region) {}
        override fun continueToDigest() {}
        override fun selectDigest(mode: DigestMode) {}
        override fun back() = true
        override fun finish(notificationsAllowed: Boolean) {}
    }

    private fun show() = rule.setContent {
        FounderGuruTheme(dark = false) {
            OnboardingScreen(OnboardingUiState.DigestStep(Region.IN, Region.IN, DigestMode.WEEKLY), emptyMap(), noop)
        }
    }

    @Test @Config(sdk = [26], application = Application::class)
    fun `Android 8 does not promise a permission prompt`() {
        show()
        rule.onNodeWithText("We send one a week, nothing else.").assertExists()
        rule.onNodeWithText("Android will ask", substring = true).assertDoesNotExist()
    }

    @Test @Config(sdk = [33], application = Application::class)
    fun `Android 13 explains the permission prompt`() {
        show()
        rule.onNodeWithText("Android will ask to allow notifications. We send one a week, nothing else.").assertExists()
    }
}
