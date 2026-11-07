// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.platform

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.digest.DigestMode
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.data.prefs.ThemePref
import io.github.ukpratik.founderguru.ui.source
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** About › Copy diagnostics (US-010, HLD §6): version, Android, health; no region, saved stories or URLs opened. */
class DiagnosticsTest {
    private val t = LocalDateTime.parse("2026-11-07T08:12").toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun `content`() {
        val text = Diagnostics.text(
            Diagnostics.Input(
                versionName = "0.1.0", versionCode = 100, androidRelease = "14", apiLevel = 34, theme = ThemePref.SYSTEM,
                digest = DigestMode.OFF, notificationsEnabled = false, bgRefresh = true, lastRefreshAt = t,
                sources = listOf(
                    source("inc42", Region.IN, attemptAt = t),
                    source("entrackr", Region.IN, status = "TIMEOUT", attemptAt = t).copy(consecutiveFailures = 2),
                    source("restofworld", Region.GLOBAL, status = "BLOCKED", attemptAt = t).copy(lastHttpCode = 403),
                    source("producthunt", Region.GLOBAL, enabled = false),
                ),
                cachedStories = 1284,
            ),
            ZoneOffset.UTC,
        )
        assertThat(text).startsWith("FounderGuru 0.1.0 (100)\nAndroid 14 (API 34) · theme system\n")
        assertThat(text).contains("digest off · notifications blocked")
        assertThat(text).contains("background refresh on · last refresh 2026-11-07 08:12")
        assertThat(text).contains("sources 3/4 on")
        assertThat(text).containsMatch("entrackr +timeout +2026-11-07 08:12 · fails 2")
        assertThat(text).containsMatch("restofworld +blocked 403 ")
        assertThat(text).containsMatch("producthunt +off ")
        assertThat(text).endsWith("cache 1284 stories")
        // No region, no URLs.
        assertThat(text).doesNotContain("region")
        assertThat(text).doesNotContain("https://")
    }
}
