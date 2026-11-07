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
import io.github.ukpratik.founderguru.data.AppRefresher
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

/** Manual DI (ADR-004): every long-lived object, built once in [FounderGuruApp]. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock = SystemClock
    val catalog: SourcesCatalog = context.assets.open(SOURCES_ASSET).bufferedReader().use { CatalogLoader.load(it.readText()) }
    val db: FgDatabase = FgDatabase.build(context)
    val settings = SettingsStore(PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SettingsStore.FILE) })
    private val policy = HttpPolicy(userAgent = HttpPolicy.userAgent(BuildConfig.VERSION_NAME))
    val httpClient = policy.newClient()

    /** Thumbnails (LLD §4.14): the same honest client, following redirects (https only by the network config). */
    val imageClient: OkHttpClient = httpClient.newBuilder().followRedirects(true).followSslRedirects(false).build()
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
    val refresher = AppRefresher(refreshRepository, settings, clock, appScope, startup)
    val visits = VisitTracker(settings, clock, appScope)
    val messages = UserMessages()

    companion object {
        const val SOURCES_ASSET = "sources.json"
    }
}
