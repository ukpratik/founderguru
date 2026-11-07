// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.nav

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.ukpratik.founderguru.AppContainer
import io.github.ukpratik.founderguru.R
import io.github.ukpratik.founderguru.core.feed.Banner
import io.github.ukpratik.founderguru.core.feed.LensFilter
import io.github.ukpratik.founderguru.core.model.StoryRow
import io.github.ukpratik.founderguru.core.tag.Sector
import io.github.ukpratik.founderguru.data.Tab
import io.github.ukpratik.founderguru.data.UserMessage
import io.github.ukpratik.founderguru.platform.ArticleOpener
import io.github.ukpratik.founderguru.platform.Sharer
import io.github.ukpratik.founderguru.ui.components.FgIcon
import io.github.ukpratik.founderguru.ui.components.Hairline
import io.github.ukpratik.founderguru.ui.components.StoryRowActions
import io.github.ukpratik.founderguru.ui.components.isLargeFont
import io.github.ukpratik.founderguru.ui.feed.FeedCallbacks
import io.github.ukpratik.founderguru.ui.feed.FeedScreen
import io.github.ukpratik.founderguru.ui.feed.FeedUiState
import io.github.ukpratik.founderguru.ui.feed.FeedViewModel
import io.github.ukpratik.founderguru.ui.hot.HotResultsScreen
import io.github.ukpratik.founderguru.ui.hot.HotViewModel
import io.github.ukpratik.founderguru.ui.stub.StubScreen
import io.github.ukpratik.founderguru.ui.theme.Fg
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Type-safe routes (LLD §5). What's hot and Search are children of a tab; Saved and Settings are top level. */
@Serializable data object LocalFeed

@Serializable data object GlobalFeed

@Serializable data object Saved

@Serializable data object SettingsHome

/** Enum arguments are passed by name (no enum serializers to keep from R8). */
@Serializable data class Hot(val tabName: String, val sectorName: String) {
    constructor(tab: Tab, sector: Sector) : this(tab.name, sector.name)
    val tab: Tab get() = Tab.valueOf(tabName)
    val sector: Sector get() = Sector.valueOf(sectorName)
}

@Serializable data class Search(val tabName: String) {
    constructor(tab: Tab) : this(tab.name)
    val tab: Tab get() = Tab.valueOf(tabName)
}

/** Bottom-nav items (D-016): Local · Global · Saved · Settings; three without Local for "Global only". */
enum class TopLevel(@StringRes val label: Int, @DrawableRes val icon: Int, @DrawableRes val selectedIcon: Int) {
    LOCAL(R.string.nav_local, R.drawable.ic_local, R.drawable.ic_local),
    GLOBAL(R.string.nav_global, R.drawable.ic_global, R.drawable.ic_global),
    SAVED(R.string.nav_saved, R.drawable.ic_bookmark, R.drawable.ic_bookmark_filled),
    SETTINGS(R.string.nav_settings, R.drawable.ic_settings, R.drawable.ic_settings),
    ;

    val route: Any get() = when (this) {
        LOCAL -> LocalFeed
        GLOBAL -> GlobalFeed
        SAVED -> Saved
        SETTINGS -> SettingsHome
    }

    val tab: Tab? get() = when (this) {
        LOCAL -> Tab.LOCAL
        GLOBAL -> Tab.GLOBAL
        else -> null
    }

    companion object {
        fun of(tab: Tab) = if (tab == Tab.LOCAL) LOCAL else GLOBAL
    }
}

/** Home: bottom navigation, the two feeds, What's hot, and the M4 stubs. */
@Composable
fun MainScaffold(container: AppContainer, globalOnly: Boolean, dark: Boolean, message: UserMessage?, onMessageShown: (UserMessage) -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val entry by nav.currentBackStackEntryAsState()
    val current: TopLevel? = entry?.destination?.let { d ->
        when {
            d.hasRoute<LocalFeed>() -> TopLevel.LOCAL
            d.hasRoute<GlobalFeed>() -> TopLevel.GLOBAL
            d.hasRoute<Saved>() -> TopLevel.SAVED
            d.hasRoute<SettingsHome>() -> TopLevel.SETTINGS
            d.hasRoute<Hot>() -> TopLevel.of(entry!!.toRoute<Hot>().tab)
            d.hasRoute<Search>() -> TopLevel.of(entry!!.toRoute<Search>().tab)
            else -> null
        }
    }
    val items = if (globalOnly) listOf(TopLevel.GLOBAL, TopLevel.SAVED, TopLevel.SETTINGS) else TopLevel.entries
    val reselect = remember { mutableStateMapOf<Tab, Int>() }

    // Last-visit lifecycle (LLD §4.9): the feed tab in front is "visited"; leaving it or stopping the app ends the visit.
    LaunchedEffect(current) { current?.tab?.let(container.visits::enter) ?: container.visits.leave() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { container.visits.leave() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { current?.tab?.let(container.visits::enter) }

    val longSnack = remember(context) { context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true }
    val duration = if (longSnack) SnackbarDuration.Long else SnackbarDuration.Short
    val noBrowser = stringResource(R.string.snack_no_browser)
    val copyLink = stringResource(R.string.copy_link)
    val digestOff = stringResource(R.string.snack_digest_off)
    val settingsLabel = stringResource(R.string.nav_settings)

    fun go(top: TopLevel) {
        nav.navigate(top.route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(message) {
        if (message == UserMessage.DIGEST_OFF) {
            onMessageShown(message)
            if (snackbar.showSnackbar(digestOff, settingsLabel, duration = duration) == SnackbarResult.ActionPerformed) go(TopLevel.SETTINGS)
        }
    }

    val rowActions = { markOpened: (StoryRow) -> Unit, setSaved: (StoryRow, Boolean) -> Unit ->
        StoryRowActions(
            open = { row ->
                when (ArticleOpener.open(context, row.url, dark)) {
                    ArticleOpener.Result.OPENED -> markOpened(row)
                    ArticleOpener.Result.NO_BROWSER -> scope.launch {
                        if (snackbar.showSnackbar(noBrowser, copyLink, duration = duration) == SnackbarResult.ActionPerformed) copy(context, row.url)
                    }
                    ArticleOpener.Result.NOT_WEB -> Unit
                }
            },
            setSaved = setSaved,
            share = { row -> Sharer.share(context, row.title, row.url) },
            openInBrowser = { row ->
                when (ArticleOpener.openInBrowser(context, row.url)) {
                    ArticleOpener.Result.OPENED -> markOpened(row)
                    ArticleOpener.Result.NO_BROWSER -> scope.launch {
                        if (snackbar.showSnackbar(noBrowser, copyLink, duration = duration) == SnackbarResult.ActionPerformed) copy(context, row.url)
                    }
                    ArticleOpener.Result.NOT_WEB -> Unit
                }
            },
        )
    }

    val now by produceState(container.clock.now()) {
        while (true) {
            delay(MINUTE_MS)
            value = container.clock.now()
        }
    }

    Scaffold(
        containerColor = Fg.colors.bg,
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            BottomBar(items, current) { top ->
                if (top == current && top.tab != null && entry?.destination?.let { it.hasRoute<LocalFeed>() || it.hasRoute<GlobalFeed>() } == true) {
                    reselect[top.tab!!] = (reselect[top.tab!!] ?: 0) + 1
                } else {
                    go(top)
                }
            }
        },
    ) { padding ->
        NavHost(
            nav,
            startDestination = if (globalOnly) GlobalFeed else LocalFeed,
            modifier = Modifier.fillMaxSize().padding(padding).background(Fg.colors.bg),
        ) {
            composable<LocalFeed> { FeedRoute(container, Tab.LOCAL, now, reselect[Tab.LOCAL] ?: 0, nav, ::go, rowActions) }
            composable<GlobalFeed> { FeedRoute(container, Tab.GLOBAL, now, reselect[Tab.GLOBAL] ?: 0, nav, ::go, rowActions) }
            composable<Hot> { back ->
                val route = back.toRoute<Hot>()
                val vm = viewModel {
                    HotViewModel(route.tab, route.sector, container.settings, container.storyRepository::tabWindow, container.connectivity, container.storyRepository, container.clock)
                }
                val state by vm.uiState.collectAsStateWithLifecycle()
                HotResultsScreen(state, now, onBack = { nav.popBackStack() }, rowActions = rowActions(vm::markOpened, vm::setSaved))
            }
            composable<Search> {
                StubScreen(stringResource(R.string.stub_search_title), stringResource(R.string.stub_search_body), onBack = { nav.popBackStack() })
            }
            composable<Saved> { StubScreen(stringResource(R.string.nav_saved), stringResource(R.string.stub_saved_body)) }
            composable<SettingsHome> { StubScreen(stringResource(R.string.nav_settings), stringResource(R.string.stub_settings_body)) }
        }
    }
}

@Composable
private fun FeedRoute(
    container: AppContainer,
    tab: Tab,
    now: Long,
    reselect: Int,
    nav: NavHostController,
    go: (TopLevel) -> Unit,
    rowActions: ((StoryRow) -> Unit, (StoryRow, Boolean) -> Unit) -> StoryRowActions,
) {
    val vm = viewModel {
        FeedViewModel(
            tab, container.settings, container.storyRepository::tabWindow, container.db.sources().observeAll(),
            container.connectivity, container.refresher, container.visits.visit, container.storyRepository, container.clock,
        )
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val listState: LazyListState = rememberLazyListState()
    // Re-tap the selected tab: scroll to the "New since" divider, or to the top (design notes).
    LaunchedEffect(reselect) { if (reselect > 0) listState.animateScrollToItem(dividerIndex(state)) }
    FeedScreen(
        state = state,
        now = now,
        listState = listState,
        callbacks = FeedCallbacks(
            selectLens = vm::selectLens,
            refresh = vm::refresh,
            dismiss = vm::dismiss,
            openHot = { nav.navigate(Hot(tab, it.sector)) },
            seeGlobal = { lens ->
                if (lens != LensFilter.All) vm.carryLensToGlobal(lens)
                go(TopLevel.GLOBAL)
            },
            openSources = { go(TopLevel.SETTINGS) },
            openSearch = { nav.navigate(Search(tab)) },
            row = rowActions(vm::markOpened, vm::setSaved),
        ),
    )
}

/** Index of the "New since" divider in the Content list (app bar, dateline, banners, hot strip, chips first); else 0. */
private fun dividerIndex(state: FeedUiState): Int {
    val content = state as? FeedUiState.Content ?: return 0
    if (content.sections.new.isEmpty()) return 0
    val banners = content.banners.count { it !is Banner.Offline }.coerceAtMost(2)
    return 2 + banners + (if (content.hot.isEmpty()) 0 else 1) + 1
}

@Composable
private fun BottomBar(items: List<TopLevel>, current: TopLevel?, onSelect: (TopLevel) -> Unit) {
    val c = Fg.colors
    val large = isLargeFont()
    Column {
        Hairline()
        NavigationBar(containerColor = c.surface, tonalElevation = 0.dp) {
            items.forEach { item ->
                val selected = item == current
                val label = stringResource(item.label)
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(item) },
                    icon = {
                        // Labels are dropped at font scale ≥ 1.5 (design-system §3), so the icon then carries the name.
                        FgIcon(if (selected) item.selectedIcon else item.icon, if (large) label else null, if (selected) c.accent else c.meta)
                    },
                    label = if (large) null else ({ Text(label, style = MaterialTheme.typography.labelMedium) }),
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = c.accentSoft,
                        selectedTextColor = c.ink,
                        unselectedTextColor = c.meta,
                    ),
                    modifier = Modifier.testTag("nav-${item.name}"),
                )
            }
        }
    }
}

private fun copy(context: Context, url: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(url, url))
}

private const val MINUTE_MS = 60_000L

/** The empty `bg` frame drawn until DataStore has loaded (LLD §5). */
@Composable
fun BlankFrame() = Box(Modifier.fillMaxSize().background(Fg.colors.bg))
