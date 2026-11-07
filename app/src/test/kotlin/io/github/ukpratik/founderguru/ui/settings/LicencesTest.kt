// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** US-013 (M5): the licences screen reads the AboutLibraries report that the build generates. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LicencesTest {
    @Test fun `report lists libraries, the bundled fonts and icons, with licence texts`() {
        val entries = Licences.read(ApplicationProvider.getApplicationContext<Application>().resources)
        val names = entries.map { it.name }
        assertThat(names).containsAtLeast("Newsreader", "Inter", "Material Symbols", "okhttp")
        assertThat(entries.first { it.name == "Newsreader" }.licences.single().text).contains("SIL OPEN FONT LICENSE")
        assertThat(entries.flatMap { it.licences }.all { it.text.isNotBlank() }).isTrue()
        assertThat(names).isInOrder(compareBy<String> { it.lowercase() })
    }

    @Test fun `parse tolerates a library without licences`() {
        val json = """{"libraries":[{"uniqueId":"x:y","name":"","artifactVersion":"1"}],"licenses":{}}"""
        assertThat(Licences.parse(json).single()).isEqualTo(LicenceEntry("x:y", "1", emptyList()))
    }
}
