// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.platform

import io.github.ukpratik.founderguru.core.digest.DigestMode
import io.github.ukpratik.founderguru.data.db.SourceStateEntity
import io.github.ukpratik.founderguru.data.prefs.ThemePref
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * About › Copy diagnostics (US-010, NFR-010, HLD §6): app version, Android version, settings that affect fetching,
 * last refresh and per-source health. Never the region, saved stories, opened links or search terms.
 */
object Diagnostics {
    data class Input(
        val versionName: String,
        val versionCode: Int,
        val androidRelease: String,
        val apiLevel: Int,
        val theme: ThemePref,
        val digest: DigestMode,
        val notificationsEnabled: Boolean,
        val bgRefresh: Boolean,
        val lastRefreshAt: Long?,
        val sources: List<SourceStateEntity>,
        val cachedStories: Int,
    )

    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    fun text(i: Input, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        fun at(ms: Long?) = ms?.let { Instant.ofEpochMilli(it).atZone(zone).format(TIME) } ?: "never"
        appendLine("FounderGuru ${i.versionName} (${i.versionCode})")
        appendLine("Android ${i.androidRelease} (API ${i.apiLevel}) · theme ${i.theme.name.lowercase()}")
        appendLine("digest ${i.digest.name.lowercase()} · notifications ${if (i.notificationsEnabled) "allowed" else "blocked"}")
        appendLine("background refresh ${if (i.bgRefresh) "on" else "off"} · last refresh ${at(i.lastRefreshAt)}")
        val sorted = i.sources.sortedWith(compareBy({ it.region }, { it.id }))
        appendLine("sources ${sorted.count { it.enabled }}/${sorted.size} on")
        val width = (sorted.maxOfOrNull { it.id.length } ?: 0) + 2
        sorted.forEach { s ->
            val status = when {
                !s.enabled -> "off"
                s.lastStatus == null -> "not yet"
                else -> s.lastStatus.lowercase() + (s.lastHttpCode?.let { " $it" } ?: "")
            }
            val fails = if (s.consecutiveFailures > 0) " · fails ${s.consecutiveFailures}" else ""
            appendLine("${s.id.padEnd(width)}${status.padEnd(16)}${at(s.lastAttemptAt)}$fails")
        }
        append("cache ${i.cachedStories} stories")
    }
}
