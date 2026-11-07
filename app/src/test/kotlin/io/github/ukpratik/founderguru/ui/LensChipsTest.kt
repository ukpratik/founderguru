// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.ukpratik.founderguru.core.feed.LensFilter
import io.github.ukpratik.founderguru.core.model.Lens
import io.github.ukpratik.founderguru.ui.components.LensChips
import io.github.ukpratik.founderguru.ui.theme.FounderGuruTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BUG-007 (US-005): a remembered lens is on screen after relaunch, so the user can tell a filter is on. Robolectric
 * measures text far narrower than a device, so a narrow screen stands in for a phone where Playbooks is off screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w200dp-h800dp-xxhdpi")
class LensChipsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `the selected chip is scrolled into view on first composition`() {
        rule.setContent { FounderGuruTheme(dark = false) { LensChips(LensFilter.Only(Lens.PLAYBOOKS), enabled = true, onSelect = {}) } }
        rule.onNodeWithText("Playbooks", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Playbooks").assertIsSelected()
    }

    @Test fun `a visible selected chip does not move the row`() {
        rule.setContent { FounderGuruTheme(dark = false) { LensChips(LensFilter.Only(Lens.FUNDING), enabled = true, onSelect = {}) } }
        rule.onNodeWithText("All", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Funding", useUnmergedTree = true).assertIsDisplayed()
    }
}
