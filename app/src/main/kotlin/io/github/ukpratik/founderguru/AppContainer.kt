// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru

import android.content.Context
import android.util.Xml
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.ukpratik.founderguru.core.fetch.FeedFetcher
import io.github.ukpratik.founderguru.core.fetch.HttpPolicy
import io.github.ukpratik.founderguru.core.model.SourcesCatalog
import io.github.ukpratik.founderguru.core.sources.CatalogLoader
import io.github.ukpratik.founderguru.core.tag.Tagger
import io.github.ukpratik.founderguru.core.time.SystemClock
import androidx.work.WorkManager
import io.github.ukpratik.founderguru.core.digest.DigestMode
import io.github.ukpratik.founderguru.core.feed.LensFilter
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.data.AppRefresher
import io.github.ukpratik.founderguru.data.ForegroundRefresh
import io.github.ukpratik.founderguru.data.Tab
import io.github.ukpratik.founderguru.platform.DigestNotifier
import io.github.ukpratik.founderguru.ui.components.Labels
import io.github.ukpratik.founderguru.work.DigestRunner
import io.github.ukpratik.founderguru.work.FgWorkerFactory
import io.github.ukpratik.founderguru.work.WorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import io.github.ukpratik.founderguru.data.RefreshRepository
import io.github.ukpratik.founderguru.data.SourcesRepository
import io.github.ukpratik.founderguru.data.StoryRepository
import io.github.ukpratik.founderguru.data.UserMessages
import io.github.ukpratik.founderguru.data.VisitTracker
import io.github.ukpratik.founderguru.data.db.FgDatabase
import io.github.ukpratik.founderguru.data.prefs.SettingsStore
import io.github.ukpratik.founderguru.platform.ConnectivityMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual DI (ADR-004): every long-lived object, built once in [FounderGuruApp]. */
class AppContainer(context: Context, workManager: (Context) -> WorkManager = { WorkManager.getInstance(it) }) {
    private val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock = SystemClock
    val catalog: SourcesCatalog = context.assets.open(SOURCES_ASSET).bufferedReader().use { CatalogLoader.load(it.readText()) }
    val db: FgDatabase = FgDatabase.build(context)
    val settings = SettingsStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SettingsStore.FILE) })
    private val policy = HttpPolicy(userAgent = HttpPolicy.userAgent(BuildConfig.VERSION_NAME))
    val httpClient = policy.newClient()

    /**
     * Thumbnails (LLD §4.14): the same honest client, following redirects (https only by the network config). No call
     * timeout (BUG-004): full-size feed images (0.7–2.4 MB) take longer than 10 s on slow links; the connect and read
     * timeouts still end a stalled download. Feeds keep the 10 s call timeout.
     */
    val imageClient: OkHttpClient = httpClient.newBuilder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()
    val fetcher = FeedFetcher(httpClient, parserFactory = { Xml.newPullParser() }, policy = policy)
    val sourcesRepository = SourcesRepository(db)
    val refreshRepository = RefreshRepository(db, fetcher, catalog, clock)
    val storyRepository = StoryRepository(db, catalog, clock)
    val connectivity = ConnectivityMonitor(context)

    /** At each start (LLD §2.4, §4.10): sync the catalogue, then re-tag stored stories if the rules changed. */
    val startup: Job = appScope.launch {
        sourcesRepository.sync(catalog)
        if (settings.current().taggerVersion < Tagger.TAGGER_VERSION) {
            storyRepository.retagAll()
            settings.setTaggerVersion(Tagger.TAGGER_VERSION)
        }
    }
    val refresher = AppRefresher(refreshRepository, settings, connectivity, clock, appScope, startup)
    val foreground = ForegroundRefresh(settings, connectivity, refresher, clock, appScope)
    val visits = VisitTracker(settings, clock, appScope)
    val messages = UserMessages()
    val notifier = DigestNotifier(appContext)
    val scheduler = WorkScheduler({ workManager(appContext) }, clock)
    val digestRunner = DigestRunner(
        settings, refresher, storyRepository::tabWindow, notifier, scheduler,
        regionName = { r -> if (r == Region.GLOBAL) null else appContext.getString(Labels.region(r)) },
        clock = clock,
    )
    val workerFactory = FgWorkerFactory({ refresher }, { digestRunner })

    /** The tab a digest notification tap opens (LLD §4.9 "Digest landing"); consumed by the navigation. */
    val digestLanding = MutableStateFlow<Tab?>(null)

    init {
        // Scheduling follows the stored settings (LLD §4.11): one source of truth for onboarding, Settings and start.
        appScope.launch {
            settings.settings.map { it.onboardingDone && it.bgRefresh }.distinctUntilChanged().collect { scheduler.setBackgroundRefresh(it) }
        }
        appScope.launch {
            var atStart = true
            settings.settings.map { if (it.onboardingDone) it.digest else DigestMode.OFF }.distinctUntilChanged().collect { mode ->
                if (mode != DigestMode.OFF && !notifier.canPost()) {
                    settings.setDigest(DigestMode.OFF) // notifications blocked since: Off everywhere (D-017)
                } else {
                    scheduler.scheduleDigest(mode, keep = atStart)
                }
                atStart = false
            }
        }
    }

    /** A digest notification was tapped: that tab's lens goes to All so the divider shows the notification's n. */
    fun openFromDigest(tab: Tab) {
        appScope.launch {
            settings.setLens(tab, LensFilter.All)
            digestLanding.value = tab
        }
    }

    companion object {
        const val SOURCES_ASSET = "sources.json"
    }
}
