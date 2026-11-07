// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** BUG-005: aapt trims unquoted leading and trailing spaces, which joined the onboarding headline parts. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StringsTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test fun `onboarding headlines keep the space before the italic part`() {
        assertThat(context.getString(R.string.onboarding_headline) + context.getString(R.string.onboarding_headline_em))
            .isEqualTo("Your startup ecosystem, every morning.")
        assertThat(context.getString(R.string.onboarding_digest_headline) + context.getString(R.string.onboarding_digest_headline_em))
            .isEqualTo("Get a weekly catch-up?")
    }

    @Test fun `no string relies on an unquoted leading or trailing space`() {
        val xml = File("src/main/res/values/strings.xml").readText()
        val offenders = Regex("""<(string|item)\b[^>]*>([^<"]*)</\1>""").findAll(xml)
            .map { it.groupValues[2] }
            .filter { it != it.trim() }
            .toList()
        assertThat(offenders).isEmpty()
    }
}
