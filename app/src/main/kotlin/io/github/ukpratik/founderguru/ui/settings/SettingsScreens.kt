// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.ukpratik.founderguru.R
import io.github.ukpratik.founderguru.core.digest.DigestMode
import io.github.ukpratik.founderguru.core.model.FailureKind
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.SourceDef
import io.github.ukpratik.founderguru.data.prefs.ThemePref
import io.github.ukpratik.founderguru.ui.components.BackBar
import io.github.ukpratik.founderguru.ui.components.FeedAppBar
import io.github.ukpratik.founderguru.ui.components.FgBanner
import io.github.ukpratik.founderguru.ui.components.FgButton
import io.github.ukpratik.founderguru.ui.components.FgIcon
import io.github.ukpratik.founderguru.ui.components.FgTextButton
import io.github.ukpratik.founderguru.ui.components.Hairline
import io.github.ukpratik.founderguru.ui.components.Labels
import io.github.ukpratik.founderguru.ui.components.Margin
import io.github.ukpratik.founderguru.ui.components.RadioRow
import io.github.ukpratik.founderguru.ui.components.ScreenTitle
import io.github.ukpratik.founderguru.ui.components.SectionHeader
import io.github.ukpratik.founderguru.ui.components.SettingsRow
import io.github.ukpratik.founderguru.ui.components.StatusBarSpacer
import io.github.ukpratik.founderguru.ui.components.SwitchRow
import io.github.ukpratik.founderguru.ui.components.Tag
import io.github.ukpratik.founderguru.ui.components.Times
import io.github.ukpratik.founderguru.ui.components.Trailing
import io.github.ukpratik.founderguru.ui.theme.Fg

/** Settings home (SCR-07): four short groups; every row shows its current value. */
@Composable
fun SettingsHomeScreen(
    state: SettingsUiState?,
    version: String,
    onRegion: () -> Unit,
    onSources: () -> Unit,
    onDigest: () -> Unit,
    onAbout: () -> Unit,
    onBgRefresh: (Boolean) -> Unit,
    onTheme: (ThemePref) -> Unit,
) {
    val c = Fg.colors
    var themeDialog by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        StatusBarSpacer()
        if (state == null) return@Column
        LazyColumn(Modifier.fillMaxSize().testTag("settings")) {
            item { Header(stringResource(R.string.app_name), stringResource(R.string.settings_title)) }
            item { SectionHeader(stringResource(R.string.settings_group_news)) }
            item { SettingsRow(R.drawable.ic_map, stringResource(R.string.settings_region), stringResource(Labels.region(state.region)), onRegion) }
            item { SettingsRow(R.drawable.ic_rss, stringResource(R.string.settings_sources), sourcesLine(state), onSources, warning = state.sources.failed + state.sources.blocked > 0) }
            item { SectionHeader(stringResource(R.string.settings_group_updates)) }
            item {
                val blocked = state.digest == DigestMode.OFF && state.notificationsBlocked
                SettingsRow(R.drawable.ic_notifications, stringResource(R.string.settings_digest), digestLine(state.digest, blocked), onDigest, warning = blocked)
            }
            item {
                SwitchRow(
                    R.drawable.ic_refresh,
                    stringResource(R.string.settings_bg_refresh),
                    stringResource(if (state.bgRefresh) R.string.settings_bg_refresh_on else R.string.settings_bg_refresh_off),
                    state.bgRefresh,
                    onBgRefresh,
                    Modifier.testTag("bg-refresh"),
                )
            }
            item { SectionHeader(stringResource(R.string.settings_group_look)) }
            item { SettingsRow(R.drawable.ic_theme, stringResource(R.string.settings_theme), stringResource(themeLabel(state.theme)), { themeDialog = true }, trailing = Trailing.NONE) }
            item { SectionHeader(stringResource(R.string.settings_group_app)) }
            item { SettingsRow(R.drawable.ic_info, stringResource(R.string.settings_about), stringResource(R.string.settings_about_value, version), onAbout) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (themeDialog) ThemeDialog(state?.theme ?: ThemePref.SYSTEM, onDismiss = { themeDialog = false }, onOk = { onTheme(it); themeDialog = false })
}

@Composable
private fun Header(overline: String, title: String) {
    Column {
        FeedAppBar(overline, title, onSearch = null)
        Spacer(Modifier.height(6.dp))
        Hairline(Modifier.padding(horizontal = Margin), color = Fg.colors.ink)
    }
}

@Composable
private fun sourcesLine(state: SettingsUiState): String {
    val s = state.sources
    val counts = if (state.region == Region.GLOBAL) {
        stringResource(R.string.settings_sources_value_global, s.globalOn)
    } else {
        stringResource(R.string.settings_sources_value, s.regionOn, stringResource(Labels.region(state.region)), s.globalOn)
    }
    val problems = listOfNotNull(
        if (s.failed > 0) pluralStringResource(R.plurals.settings_sources_failed, s.failed, s.failed) else null,
        if (s.blocked > 0) pluralStringResource(R.plurals.settings_sources_blocked, s.blocked, s.blocked) else null,
    )
    return if (problems.isEmpty()) counts else "$counts · ${problems.joinToString(", ")}"
}

@Composable
private fun digestLine(mode: DigestMode, blocked: Boolean): String = when {
    blocked -> stringResource(R.string.settings_digest_blocked)
    mode == DigestMode.WEEKLY -> stringResource(R.string.settings_digest_weekly)
    mode == DigestMode.DAILY -> stringResource(R.string.settings_digest_daily)
    else -> stringResource(R.string.settings_digest_off)
}

private fun themeLabel(t: ThemePref) = when (t) {
    ThemePref.SYSTEM -> R.string.theme_system
    ThemePref.LIGHT -> R.string.theme_light
    ThemePref.DARK -> R.string.theme_dark
}

/** Theme dialog (design state 07): radio list, applies on OK, no restart. */
@Composable
private fun ThemeDialog(current: ThemePref, onDismiss: () -> Unit, onOk: (ThemePref) -> Unit) {
    val c = Fg.colors
    var choice by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.overlay,
        shape = MaterialTheme.shapes.large,
        title = { Text(stringResource(R.string.settings_theme), color = c.ink, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                ThemePref.entries.forEach { t ->
                    RadioRow(
                        stringResource(themeLabel(t)),
                        if (t == ThemePref.SYSTEM) stringResource(R.string.theme_system_sub) else null,
                        selected = choice == t,
                        onClick = { choice = t },
                        modifier = Modifier.testTag("theme-${t.name}"),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onOk(choice) }) { Text(stringResource(R.string.ok), color = c.accent, style = MaterialTheme.typography.labelLarge) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = c.accent, style = MaterialTheme.typography.labelLarge) } },
    )
}

/** Region redo (SCR-01 reused): the onboarding list with a back arrow and Save. */
@Composable
fun RegionScreen(current: Region, sourceNames: Map<Region, List<String>>, onBack: () -> Unit, onSave: (Region) -> Unit) {
    val c = Fg.colors
    var selected by rememberSaveable { mutableStateOf(current) }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        StatusBarSpacer()
        BackBar(stringResource(R.string.settings_title), onBack)
        LazyColumn(Modifier.weight(1f)) {
            item { ScreenTitle(stringResource(R.string.settings_region), stringResource(R.string.region_intro)) }
            items(Labels.ONBOARDING_ORDER) { r ->
                val sub = if (r == Region.GLOBAL) stringResource(R.string.region_global_sub) else regionSources(sourceNames[r].orEmpty())
                RadioRow(
                    stringResource(Labels.region(r)),
                    sub,
                    selected = selected == r,
                    onClick = { selected = r },
                    modifier = Modifier.testTag("region-${r.name}"),
                    tag = if (r in Labels.LIMITED) ({ Tag(stringResource(R.string.tag_limited), filled = false) }) else null,
                )
                Hairline(Modifier.padding(horizontal = Margin))
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            FgButton(stringResource(R.string.save_button), { onSave(selected) }, Modifier.fillMaxWidth().testTag("region-save"))
        }
    }
}

private fun regionSources(names: List<String>): String = if (names.size <= 4) names.joinToString(" · ") else names.take(3).joinToString(" · ") + " · +${names.size - 3}"

/** Sources with health (SCR-08): grouped by region (the user's first), icon + words + time for every status. */
@Composable
fun SourcesScreen(state: SourcesUiState?, now: Long, onBack: () -> Unit, onToggle: (String, Boolean) -> Unit) {
    val c = Fg.colors
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(c.bg)) {
        StatusBarSpacer()
        BackBar(stringResource(R.string.settings_title), onBack)
        if (state == null) return@Column
        LazyColumn(Modifier.fillMaxSize().testTag("sources")) {
            item {
                val intro = state.lastRefreshAt?.let { stringResource(R.string.sources_intro_status, Times.clock(context, it)) } ?: stringResource(R.string.sources_intro)
                ScreenTitle(stringResource(R.string.settings_sources), intro)
            }
            state.allRegionOff?.let { region ->
                item {
                    FgBanner(
                        icon = R.drawable.ic_warning,
                        warning = true,
                        lead = "",
                        body = stringResource(R.string.sources_all_off, stringResource(Labels.region(region))),
                        action = null,
                        onAction = {},
                        onDismiss = null,
                    )
                }
            }
            state.groups.forEach { group ->
                item(key = "group-${group.region}") {
                    val name = if (group.region == Region.GLOBAL) stringResource(R.string.sources_group_global) else stringResource(Labels.region(group.region))
                    SectionHeader(stringResource(R.string.sources_group, name, group.on, group.items.size), c.meta)
                }
                items(group.items, key = { it.id }) { item ->
                    SwitchRow(
                        icon = null,
                        title = item.name,
                        supporting = "",
                        checked = item.enabled,
                        onChange = { onToggle(item.id, it) },
                        modifier = Modifier.testTag("source-${item.id}"),
                        supportingContent = { HealthLine(item, now) },
                    )
                    Hairline(Modifier.padding(horizontal = Margin))
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun HealthLine(item: SourceItem, now: Long) {
    val c = Fg.colors
    val context = LocalContext.current
    fun at(ms: Long?) = ms?.let { Times.visit(context, it, now) }.orEmpty()
    val (icon, tint, text) = when (val h = item.health) {
        is Health.Ok -> Triple(R.drawable.ic_check_circle, c.meta, stringResource(R.string.health_ok, at(h.at)).let { t ->
            item.viaHost?.let { stringResource(R.string.health_via, t, it) } ?: t
        })
        is Health.Failed -> Triple(R.drawable.ic_error, c.accent, stringResource(R.string.health_failed, reason(h.kind, h.httpCode), at(h.at)))
        is Health.Blocked -> Triple(
            R.drawable.ic_block,
            c.ink2,
            h.httpCode?.let { stringResource(R.string.health_blocked, it, at(h.at)) } ?: stringResource(R.string.health_blocked_plain, at(h.at)),
        )
        Health.Off -> Triple(null, c.meta, stringResource(R.string.health_off))
        Health.Never -> Triple(null, c.meta, stringResource(R.string.health_never))
    }
    Column(Modifier.padding(top = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                FgIcon(icon, null, tint, size = 15.dp)
                Spacer(Modifier.size(6.dp))
            }
            Text(text, color = tint, style = MaterialTheme.typography.bodySmall)
        }
        val newest = (item.health as? Health.Failed)?.newestItemAt
        if (newest != null) Text(stringResource(R.string.health_newest, at(newest)), color = c.meta, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun reason(kind: FailureKind, code: Int?): String = when (kind) {
    FailureKind.TIMEOUT -> stringResource(R.string.reason_timeout)
    FailureKind.NETWORK -> stringResource(R.string.reason_network)
    FailureKind.HTTP, FailureKind.BLOCKED -> code?.let { stringResource(R.string.reason_http, it) } ?: stringResource(R.string.reason_http_plain)
    FailureKind.NOT_A_FEED, FailureKind.PARSE -> stringResource(R.string.reason_format)
    FailureKind.TOO_LARGE -> stringResource(R.string.reason_too_large)
    FailureKind.REDIRECTS, FailureKind.INSECURE -> stringResource(R.string.reason_redirect)
}

/** Digest frequency (design state 06): radio list, a live preview of the real text and the delivery note. */
@Composable
fun DigestScreen(state: SettingsUiState?, onBack: () -> Unit, vm: SettingsViewModel) {
    val c = Fg.colors
    val context = LocalContext.current
    var pending by remember { mutableStateOf(DigestMode.OFF) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.onPermissionResult(pending, granted) }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        StatusBarSpacer()
        BackBar(stringResource(R.string.settings_title), onBack)
        if (state == null) return@Column
        val world = state.region == Region.GLOBAL
        val place = stringResource(Labels.region(state.region))
        LazyColumn(Modifier.fillMaxSize().testTag("digest")) {
            item {
                ScreenTitle(
                    stringResource(R.string.settings_digest),
                    if (world) stringResource(R.string.digest_intro_world) else stringResource(R.string.digest_intro, place),
                )
            }
            val options = listOf(
                Triple(DigestMode.WEEKLY, R.string.digest_weekly, R.string.digest_weekly_sub),
                Triple(DigestMode.DAILY, R.string.digest_daily, R.string.digest_daily_sub),
                Triple(DigestMode.OFF, R.string.digest_off, null),
            )
            options.forEach { (mode, name, sub) ->
                item(key = mode.name) {
                    RadioRow(stringResource(name), sub?.let { stringResource(it) }, state.digest == mode, onClick = {
                        val canAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        if (vm.chooseDigest(mode, canAsk) == DigestAsk.REQUEST_PERMISSION && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pending = mode
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }, modifier = Modifier.testTag("digest-${mode.name}"))
                    Hairline(Modifier.padding(horizontal = Margin))
                }
            }
            if (state.digest == DigestMode.OFF && state.notificationsBlocked) {
                item {
                    Column(Modifier.padding(start = Margin, end = Margin, top = 14.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                        Text(stringResource(R.string.settings_digest_blocked), color = c.accent, style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.settings_digest_blocked_help), color = c.ink2, style = MaterialTheme.typography.bodySmall)
                        FgTextButton(stringResource(R.string.open_system_settings), { openNotificationSettings(context) })
                    }
                }
            }
            if (state.digest != DigestMode.OFF) {
                item {
                    val sample = when {
                        state.digest == DigestMode.DAILY && world -> stringResource(R.string.onboarding_sample_daily_world)
                        state.digest == DigestMode.DAILY -> stringResource(R.string.onboarding_sample_daily, place)
                        world -> stringResource(R.string.onboarding_sample_weekly_world)
                        else -> stringResource(R.string.onboarding_sample_weekly, place)
                    }
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp).fillMaxWidth().background(c.surface2, MaterialTheme.shapes.small).padding(14.dp)) {
                        SectionLabel(stringResource(R.string.digest_preview))
                        Text(sample, Modifier.padding(top = 6.dp), color = c.ink, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { Text(stringResource(R.string.digest_late_note), Modifier.padding(Margin), color = c.meta, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) = io.github.ukpratik.founderguru.ui.components.Kicker(text, Fg.colors.meta)

/** "Open system settings" fires `ACTION_APP_NOTIFICATION_SETTINGS` (design notes). */
fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }
}

/** About (SCR-09, US-010, US-013, NFR-010): licence, privacy, sources, every third-party host, help and diagnostics. */
@Composable
fun AboutScreen(
    version: String,
    sourceCount: Int,
    hosts: List<String>,
    donateUrl: String?,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onSources: () -> Unit,
    onCopyDiagnostics: () -> Unit,
    onLicences: () -> Unit = {},
) {
    val c = Fg.colors
    Column(Modifier.fillMaxSize().background(c.bg)) {
        StatusBarSpacer()
        BackBar(stringResource(R.string.settings_title), onBack)
        LazyColumn(Modifier.fillMaxSize().testTag("about")) {
            item { ScreenTitle(stringResource(R.string.about_title), stringResource(R.string.about_lede, version)) }
            item {
                SettingsRow(R.drawable.ic_lock, stringResource(R.string.about_privacy), stringResource(R.string.about_privacy_body), null, trailing = Trailing.NONE)
            }
            item {
                SettingsRow(R.drawable.ic_code, stringResource(R.string.about_code), stringResource(R.string.about_code_sub), { onOpen(AboutLinks.REPO) }, trailing = Trailing.EXTERNAL)
            }
            item {
                SettingsRow(R.drawable.ic_info, stringResource(R.string.about_licences), stringResource(R.string.about_licences_sub), onLicences, Modifier.testTag("licences-row"))
            }
            item { SettingsRow(R.drawable.ic_rss, stringResource(R.string.about_sources), pluralStringResource(R.plurals.about_sources_sub, sourceCount, sourceCount), onSources) }
            item {
                val proxy = stringResource(R.string.about_hosts_proxy, SourcesMapper.PROXY)
                val list = hosts.joinToString(" · ") { if (it == SourcesMapper.PROXY) proxy else it }
                SettingsRow(R.drawable.ic_global, stringResource(R.string.about_hosts), stringResource(R.string.about_hosts_lead), null, trailing = Trailing.NONE, below = {
                    Text(list, Modifier.padding(top = 6.dp), color = c.ink2, style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.about_images), Modifier.padding(top = 6.dp), color = c.ink2, style = MaterialTheme.typography.bodySmall)
                })
            }
            item { SectionHeader(stringResource(R.string.about_group_help)) }
            item {
                SettingsRow(R.drawable.ic_mail, stringResource(R.string.about_feedback), stringResource(R.string.about_feedback_sub), { onOpen(AboutLinks.ISSUES) }, trailing = Trailing.EXTERNAL)
            }
            // No donation link in 1.0 (D-028): the row appears only once a URL is set.
            if (donateUrl != null) {
                item {
                    SettingsRow(R.drawable.ic_heart, stringResource(R.string.about_donate), stringResource(R.string.about_donate_sub), { onOpen(donateUrl) }, trailing = Trailing.EXTERNAL)
                }
            }
            item {
                SettingsRow(
                    R.drawable.ic_copy,
                    stringResource(R.string.about_diagnostics),
                    stringResource(R.string.about_diagnostics_sub),
                    onCopyDiagnostics,
                    Modifier.testTag("copy-diagnostics"),
                    trailing = Trailing.NONE,
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** About › Sources: every feed with a link to its publisher (NFR-010). */
@Composable
fun AboutSourcesScreen(sources: List<SourceDef>, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val c = Fg.colors
    Column(Modifier.fillMaxSize().background(c.bg)) {
        StatusBarSpacer()
        BackBar(stringResource(R.string.about_title), onBack)
        LazyColumn(Modifier.fillMaxSize()) {
            item { ScreenTitle(stringResource(R.string.about_sources_title), stringResource(R.string.about_sources_intro)) }
            Labels.ONBOARDING_ORDER.forEach { region ->
                val group = sources.filter { it.region == region }
                if (group.isEmpty()) return@forEach
                item(key = "h-$region") {
                    val name = if (region == Region.GLOBAL) stringResource(R.string.sources_group_global) else stringResource(Labels.region(region))
                    SectionHeader(name, c.meta)
                }
                items(group, key = { it.id }) { s ->
                    SettingsRow(null, s.name, SourcesMapper.hostOf(s.homepage), { onOpen(s.homepage) }, trailing = Trailing.EXTERNAL)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Project links (D-023 G-8). No donation link in 1.0 (D-028); set DONATE to show the About row. */
object AboutLinks {
    const val REPO = "https://github.com/ukpratik/founderguru"
    const val ISSUES = "$REPO/issues"
    val DONATE: String? = null
}
