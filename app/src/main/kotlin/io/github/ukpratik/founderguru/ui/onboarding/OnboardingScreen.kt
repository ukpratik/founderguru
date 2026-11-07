// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.ukpratik.founderguru.R
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.digest.DigestMode
import io.github.ukpratik.founderguru.ui.components.FgButton
import io.github.ukpratik.founderguru.ui.components.FgIcon
import io.github.ukpratik.founderguru.ui.components.Hairline
import io.github.ukpratik.founderguru.ui.components.Kicker
import io.github.ukpratik.founderguru.ui.components.Labels
import io.github.ukpratik.founderguru.ui.components.StatusBarSpacer
import io.github.ukpratik.founderguru.ui.components.Tag
import io.github.ukpratik.founderguru.ui.theme.Fg
import io.github.ukpratik.founderguru.ui.theme.FgType

/** Onboarding (SCR-01, SCR-02): region, then the optional digest. [sourceNames] lists each region's sources. */
@Composable
fun OnboardingScreen(state: OnboardingUiState, sourceNames: Map<Region, List<String>>, vm: OnboardingActions) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.finish(granted) }
    BackHandler(enabled = state is OnboardingUiState.DigestStep) { vm.back() }
    when (state) {
        is OnboardingUiState.RegionStep -> RegionStep(state, sourceNames, vm)
        is OnboardingUiState.DigestStep -> DigestStep(state, vm) {
            val needsPermission = state.choice != DigestMode.OFF && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.finish(true)
        }
        OnboardingUiState.Done -> Box(Modifier.fillMaxSize().background(Fg.colors.bg))
    }
}

/** The ViewModel surface the screen uses (lets tests drive the screen without a ViewModel). */
interface OnboardingActions {
    fun selectRegion(region: Region)
    fun continueToDigest()
    fun selectDigest(mode: DigestMode)
    fun back(): Boolean
    fun finish(notificationsAllowed: Boolean)
}

@Composable
private fun Frame(step: Int, onBack: (() -> Unit)?, cta: String, note: String, onCta: () -> Unit, content: @Composable () -> Unit) {
    val c = Fg.colors
    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.navigationBars)) {
        StatusBarSpacer()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, top = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp).padding(end = 0.dp)) {
                        FgIcon(R.drawable.ic_back, stringResource(R.string.back), c.ink2)
                    }
                } else {
                    Text(
                        buildAnnotatedString {
                            append(stringResource(R.string.onboarding_wordmark_founder))
                            withStyle(SpanStyle(fontStyle = FontStyle.Normal, fontWeight = FontWeight.SemiBold, color = c.accent)) {
                                append(stringResource(R.string.onboarding_wordmark_guru))
                            }
                        },
                        color = c.ink,
                        style = FgType.wordmark,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.onboarding_step, step), color = c.meta, style = MaterialTheme.typography.bodySmall)
            }
            content()
            Spacer(Modifier.size(16.dp))
        }
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 10.dp)) {
            FgButton(cta, onCta, Modifier.fillMaxWidth().testTag("onboarding-cta"))
            Text(
                note,
                Modifier.fillMaxWidth().padding(top = 10.dp),
                color = c.meta,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Headline(plain: String, em: String) {
    val c = Fg.colors
    Text(
        buildAnnotatedString {
            append(plain)
            withStyle(SpanStyle(color = c.accent, fontStyle = FontStyle.Italic)) { append(em) }
        },
        Modifier.padding(top = 22.dp, bottom = 12.dp),
        color = c.ink,
        style = MaterialTheme.typography.displaySmall,
    )
}

@Composable
private fun Question(label: String, hint: String) {
    val c = Fg.colors
    Column(Modifier.padding(top = 24.dp, bottom = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
            Kicker(label, c.ink, Modifier.weight(1f))
            Text(hint, color = c.meta, style = MaterialTheme.typography.bodySmall)
        }
        Hairline(color = c.ink)
    }
}

/** A radio row: the whole row is the target (`selectable(role = RadioButton)`), 58dp+. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionRow(name: String, sub: String, selected: Boolean, tag: (@Composable () -> Unit)?, onClick: () -> Unit, testTag: String) {
    val c = Fg.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .drawBehind { drawLine(c.rule, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1.dp.toPx()) }
            .padding(vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                Text(name, color = c.ink, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                tag?.invoke()
            }
            Text(sub, Modifier.padding(top = 2.dp), color = c.meta, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        RadioDot(selected)
    }
}

@Composable
private fun RadioDot(selected: Boolean) {
    val c = Fg.colors
    Box(
        Modifier.size(22.dp).border(2.dp, if (selected) c.accent else c.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(10.dp).background(c.accent, CircleShape))
    }
}

@Composable
private fun RegionStep(state: OnboardingUiState.RegionStep, sourceNames: Map<Region, List<String>>, vm: OnboardingActions) {
    val c = Fg.colors
    Frame(
        step = 1,
        onBack = null,
        cta = stringResource(R.string.onboarding_continue),
        note = stringResource(R.string.onboarding_continue_note),
        onCta = vm::continueToDigest,
    ) {
        Headline(stringResource(R.string.onboarding_headline), stringResource(R.string.onboarding_headline_em))
        Text(stringResource(R.string.onboarding_lede), color = c.ink2, style = MaterialTheme.typography.bodyLarge)
        Question(stringResource(R.string.onboarding_region_question), stringResource(R.string.onboarding_pick_one))
        Column(Modifier.selectableGroup()) {
            Labels.ONBOARDING_ORDER.forEach { region ->
                val sub = if (region == Region.GLOBAL) stringResource(R.string.onboarding_global_sub) else sourceLine(sourceNames[region].orEmpty())
                val suggested = region == state.suggested
                val limited = region in Labels.LIMITED
                OptionRow(
                    name = stringResource(Labels.region(region)),
                    sub = sub,
                    selected = region == state.selected,
                    tag = when {
                        suggested -> ({ Tag(stringResource(R.string.tag_suggested), filled = true) })
                        limited -> ({ Tag(stringResource(R.string.tag_limited), filled = false) })
                        else -> null
                    },
                    onClick = { vm.selectRegion(region) },
                    testTag = "region-${region.name}",
                )
            }
        }
    }
}

/** "Inc42 · YourStory · Entrackr · ETtech", or the first three and "+n". */
@Composable
private fun sourceLine(names: List<String>): String =
    if (names.size <= 4) names.joinToString(" · ") else (names.take(3) + stringResource(R.string.onboarding_more_sources, names.size - 3)).joinToString(" · ")

@Composable
private fun DigestStep(state: OnboardingUiState.DigestStep, vm: OnboardingActions, onCta: () -> Unit) {
    val c = Fg.colors
    val world = state.region == Region.GLOBAL
    val place = stringResource(Labels.region(state.region))
    val (cta, note) = when (state.choice) {
        DigestMode.WEEKLY -> stringResource(R.string.onboarding_cta_weekly) to stringResource(R.string.onboarding_rationale_weekly)
        DigestMode.DAILY -> stringResource(R.string.onboarding_cta_daily) to stringResource(R.string.onboarding_rationale_daily)
        DigestMode.OFF -> stringResource(R.string.onboarding_cta_start) to stringResource(R.string.onboarding_no_notifications)
    }
    Frame(step = 2, onBack = { vm.back() }, cta = cta, note = note, onCta = onCta) {
        Headline(stringResource(R.string.onboarding_digest_headline), stringResource(R.string.onboarding_digest_headline_em))
        Text(
            if (world) stringResource(R.string.onboarding_digest_lede_world) else stringResource(R.string.onboarding_digest_lede, place),
            color = c.ink2,
            style = MaterialTheme.typography.bodyLarge,
        )
        Question(stringResource(R.string.onboarding_digest_label), stringResource(R.string.onboarding_digest_change_later))
        Column(Modifier.selectableGroup()) {
            OptionRow(
                stringResource(R.string.digest_weekly), stringResource(R.string.digest_weekly_sub), state.choice == DigestMode.WEEKLY,
                { Tag(stringResource(R.string.tag_recommended), filled = true) }, { vm.selectDigest(DigestMode.WEEKLY) }, "digest-WEEKLY",
            )
            OptionRow(
                stringResource(R.string.digest_daily), stringResource(R.string.digest_daily_sub), state.choice == DigestMode.DAILY,
                null, { vm.selectDigest(DigestMode.DAILY) }, "digest-DAILY",
            )
            OptionRow(
                stringResource(R.string.digest_not_now), stringResource(R.string.digest_not_now_sub), state.choice == DigestMode.OFF,
                null, { vm.selectDigest(DigestMode.OFF) }, "digest-OFF",
            )
        }
        run {
            val daily = state.choice == DigestMode.DAILY
            val sample = when {
                world && daily -> stringResource(R.string.onboarding_sample_daily_world)
                world -> stringResource(R.string.onboarding_sample_weekly_world)
                daily -> stringResource(R.string.onboarding_sample_daily, place)
                else -> stringResource(R.string.onboarding_sample_weekly, place)
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp).background(c.surface2, MaterialTheme.shapes.extraSmall)) {
                Box(Modifier.width(2.dp).heightIn(min = 56.dp).background(c.accent))
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Kicker(stringResource(R.string.onboarding_sample_label), c.meta)
                    Text(sample, Modifier.padding(top = 4.dp), color = c.ink2, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
