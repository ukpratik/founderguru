// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.settings

import io.github.ukpratik.founderguru.core.digest.DigestMode
import io.github.ukpratik.founderguru.core.model.FailureKind
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.SourceDef
import io.github.ukpratik.founderguru.data.db.SourceStateEntity
import io.github.ukpratik.founderguru.data.prefs.ThemePref
import java.net.URI

/** Source health as Settings › Sources shows it (design SCR-08): icon + words + time, never colour alone. */
sealed interface Health {
    data class Ok(val at: Long?) : Health
    data class Failed(val kind: FailureKind, val httpCode: Int?, val at: Long?, val newestItemAt: Long?) : Health
    data class Blocked(val httpCode: Int?, val at: Long?) : Health
    data object Off : Health
    data object Never : Health
}

data class SourceItem(val id: String, val name: String, val enabled: Boolean, val health: Health, val viaHost: String?)

data class SourceGroup(val region: Region, val items: List<SourceItem>) {
    val on: Int get() = items.count { it.enabled }
}

data class SourcesUiState(val groups: List<SourceGroup>, val lastRefreshAt: Long?, val userRegion: Region) {
    /** All of the user's region's sources are off (design state 05); null otherwise or for Global only. */
    val allRegionOff: Region? get() = groups.firstOrNull { it.region == userRegion && it.region != Region.GLOBAL && it.on == 0 }?.region
}

/** The one-line summary on Settings home: "4 India + 7 global · 1 couldn't update, 1 blocked". */
data class SourcesSummary(val regionOn: Int, val globalOn: Int, val failed: Int, val blocked: Int)

data class SettingsUiState(
    val region: Region,
    val theme: ThemePref,
    val bgRefresh: Boolean,
    val digest: DigestMode,
    /** Notifications are off for the app (permission denied or blocked in system settings). */
    val notificationsBlocked: Boolean,
    val sources: SourcesSummary,
)

/** Pure mapping from the catalogue and `source_state` (JVM-tested). Shown: the user's region first, then Global. */
object SourcesMapper {
    private val FAILURES = FailureKind.entries.associateBy { it.name }
    private const val PROXY_HOST = "hnrss.org"

    fun health(s: SourceStateEntity?): Health {
        if (s == null || s.lastStatus == null) return if (s?.enabled == false) Health.Off else Health.Never
        if (!s.enabled) return Health.Off
        val kind = FAILURES[s.lastStatus]
        return when {
            kind == null -> Health.Ok(s.lastSuccessAt ?: s.lastAttemptAt)
            kind == FailureKind.BLOCKED -> Health.Blocked(s.lastHttpCode, s.lastAttemptAt)
            else -> Health.Failed(kind, s.lastHttpCode, s.lastAttemptAt, s.newestItemAt)
        }
    }

    fun groups(catalog: List<SourceDef>, states: List<SourceStateEntity>, userRegion: Region): List<SourceGroup> {
        val byId = states.associateBy { it.id }
        val regions = if (userRegion == Region.GLOBAL) listOf(Region.GLOBAL) else listOf(userRegion, Region.GLOBAL)
        return regions.map { region ->
            SourceGroup(
                region,
                catalog.filter { it.region == region }.map { def ->
                    val state = byId[def.id]
                    SourceItem(def.id, def.name, state?.enabled ?: true, health(state), hostOf(def.url).takeIf { it == PROXY_HOST })
                },
            )
        }
    }

    fun summary(groups: List<SourceGroup>): SourcesSummary {
        val on = groups.flatMap { it.items }.filter { it.enabled }
        return SourcesSummary(
            regionOn = groups.filter { it.region != Region.GLOBAL }.sumOf { it.on },
            globalOn = groups.filter { it.region == Region.GLOBAL }.sumOf { it.on },
            failed = on.count { it.health is Health.Failed },
            blocked = on.count { it.health is Health.Blocked },
        )
    }

    /** Feed hosts for About ("Who the app talks to", US-013), in catalogue order, without duplicates. */
    fun hosts(catalog: List<SourceDef>): List<String> = catalog.map { hostOf(it.url) }.distinct()

    fun hostOf(url: String): String = runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: url

    const val PROXY = PROXY_HOST
}
