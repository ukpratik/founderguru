// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru

import android.content.Context
import android.util.Xml
import io.github.ukpratik.founderguru.core.fetch.FeedFetcher
import io.github.ukpratik.founderguru.core.fetch.HttpPolicy
import io.github.ukpratik.founderguru.core.model.SourcesCatalog
import io.github.ukpratik.founderguru.core.sources.CatalogLoader
import io.github.ukpratik.founderguru.core.time.SystemClock
import io.github.ukpratik.founderguru.data.RefreshRepository
import io.github.ukpratik.founderguru.data.SourcesRepository
import io.github.ukpratik.founderguru.data.db.FgDatabase

/** Manual DI (ADR-004): every long-lived object, built once in [FounderGuruApp]. */
class AppContainer(context: Context) {
    val catalog: SourcesCatalog = context.assets.open(SOURCES_ASSET).bufferedReader().use { CatalogLoader.load(it.readText()) }
    val db: FgDatabase = FgDatabase.build(context)
    private val policy = HttpPolicy(userAgent = HttpPolicy.userAgent(BuildConfig.VERSION_NAME))
    val httpClient = policy.newClient()
    val fetcher = FeedFetcher(httpClient, parserFactory = { Xml.newPullParser() }, policy = policy)
    val sourcesRepository = SourcesRepository(db)
    val refreshRepository = RefreshRepository(db, fetcher, catalog, SystemClock)

    companion object {
        const val SOURCES_ASSET = "sources.json"
    }
}
