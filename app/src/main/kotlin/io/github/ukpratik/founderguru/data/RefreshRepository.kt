// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.data

import androidx.room.withTransaction
import io.github.ukpratik.founderguru.core.feed.DailyCap
import io.github.ukpratik.founderguru.core.fetch.FeedFetcher
import io.github.ukpratik.founderguru.core.model.FailureKind
import io.github.ukpratik.founderguru.core.model.FetchOutcome
import io.github.ukpratik.founderguru.core.model.HttpState
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.SourceDef
import io.github.ukpratik.founderguru.core.model.SourcesCatalog
import io.github.ukpratik.founderguru.core.model.StoryDraft
import io.github.ukpratik.founderguru.core.normalise.ItemNormaliser
import io.github.ukpratik.founderguru.core.normalise.TitleKey
import io.github.ukpratik.founderguru.core.tag.StartupFilter
import io.github.ukpratik.founderguru.core.tag.Tagger
import io.github.ukpratik.founderguru.core.tag.WordMatcher
import io.github.ukpratik.founderguru.core.time.Clock
import io.github.ukpratik.founderguru.data.db.FgDatabase
import io.github.ukpratik.founderguru.data.db.SourceStateEntity
import io.github.ukpratik.founderguru.data.db.StoryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.time.ZoneId

data class RefreshResult(val updated: List<String>, val notModified: List<String>, val failed: Map<String, FailureKind>) {
    val anySuccess: Boolean get() = updated.isNotEmpty() || notModified.isNotEmpty()
}

/**
 * The refresh pipeline (LLD §4.1, US-004, NFR-001/003/004/005): fetch → normalise → de-duplicate → cap → write, one
 * source at a time per transaction, at most [PARALLEL] sources in flight. One failing source never affects another.
 * Items from `needsKeywordFilter` sources must pass [StartupFilter]; every stored story is tagged by [Tagger] (M2).
 * Connectivity, triggers and progress state come in M4.
 */
class RefreshRepository(
    private val db: FgDatabase,
    private val fetcher: FeedFetcher,
    private val catalog: SourcesCatalog,
    private val clock: Clock,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val mutex = Mutex()
    private var inFlight: CompletableDeferred<RefreshResult>? = null

    /**
     * Refreshes the enabled sources of [region] plus GLOBAL ([region] null = every enabled source). A caller that
     * arrives during a run waits for that run and gets its result (no double fetch).
     */
    suspend fun refresh(region: Region?): RefreshResult {
        val (run, owner) = mutex.withLock {
            inFlight?.let { it to false } ?: CompletableDeferred<RefreshResult>().also { inFlight = it }.let { it to true }
        }
        if (!owner) return run.await()
        try {
            val result = doRefresh(region)
            run.complete(result)
            return result
        } catch (e: Throwable) {
            run.completeExceptionally(e)
            throw e
        } finally {
            mutex.withLock { inFlight = null }
        }
    }

    private suspend fun doRefresh(region: Region?): RefreshResult {
        val states = db.sources().all().associateBy { it.id }
        val targets = catalog.sources.filter { s ->
            states[s.id]?.enabled == true && (region == null || s.region == region || s.region == Region.GLOBAL)
        }
        val semaphore = Semaphore(PARALLEL)
        val outcomes = coroutineScope {
            targets.map { source ->
                async { semaphore.withPermit { source.id to refreshOne(source, states.getValue(source.id)) } }
            }.awaitAll()
        }
        prune(clock.now())
        return RefreshResult(
            updated = outcomes.filter { it.second is FetchOutcome.Updated }.map { it.first },
            notModified = outcomes.filter { it.second == FetchOutcome.NotModified }.map { it.first },
            failed = outcomes.mapNotNull { (id, o) -> (o as? FetchOutcome.Failed)?.let { id to it.kind } }.toMap(),
        )
    }

    /** One source, its own failure boundary (NFR-003). */
    private suspend fun refreshOne(source: SourceDef, state: SourceStateEntity): FetchOutcome {
        val outcome = try {
            fetcher.fetch(source, HttpState(state.etag, state.lastModified, state.finalUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            FetchOutcome.Failed(FailureKind.NETWORK, null, e.javaClass.simpleName)
        }
        return try {
            write(source, outcome)
            outcome
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val failed = FetchOutcome.Failed(FailureKind.PARSE, null, e.javaClass.simpleName)
            write(source, failed)
            failed
        }
    }

    private suspend fun write(source: SourceDef, outcome: FetchOutcome) {
        val now = clock.now()
        db.withTransaction {
            val dao = db.sources()
            val state = dao.byId(source.id) ?: return@withTransaction // removed by a catalogue sync meanwhile
            val next = when (outcome) {
                is FetchOutcome.Updated -> {
                    val drafts = outcome.items.mapNotNull { ItemNormaliser.normalise(it, outcome.fetchedUrl, now) }
                    val kept = if (source.needsKeywordFilter) drafts.filter { StartupFilter.keep(WordMatcher.text(it.title, it.snippet)) } else drafts
                    insertDrafts(source, kept, now)
                    state.copy(
                        lastStatus = STATUS_OK, lastHttpCode = 200, lastError = if (outcome.truncated) "truncated" else null,
                        lastAttemptAt = now, lastSuccessAt = now, consecutiveFailures = 0,
                        etag = outcome.etag, lastModified = outcome.lastModified,
                        finalUrl = outcome.permanentUrl ?: state.finalUrl,
                        newestItemAt = drafts.maxOfOrNull { it.publishedAt } ?: state.newestItemAt,
                    )
                }
                FetchOutcome.NotModified -> state.copy(
                    lastStatus = STATUS_NOT_MODIFIED, lastHttpCode = 304, lastError = null,
                    lastAttemptAt = now, lastSuccessAt = now, consecutiveFailures = 0,
                )
                is FetchOutcome.Failed -> state.copy(
                    lastStatus = outcome.kind.name, lastHttpCode = outcome.httpCode, lastError = outcome.message.take(200),
                    lastAttemptAt = now, consecutiveFailures = state.consecutiveFailures + 1,
                )
            }
            dao.upsert(next)
        }
    }

    /** De-duplication (US-004, D-022 OQ-B, D-023 G-2), then the daily cap, then insert. Runs inside the transaction. */
    private suspend fun insertDrafts(source: SourceDef, drafts: List<StoryDraft>, now: Long) {
        val stories = db.stories()
        val unique = drafts.distinctBy { it.urlKey }
        val known = if (unique.isEmpty()) emptySet() else stories.existingUrlKeys(unique.map { it.urlKey }).toSet()
        val fresh = unique.filter { it.urlKey !in known }
        val perDay = if (source.dailyCap == null) emptyMap() else
            DailyCap.perDay(stories.publishedSince(source.id, now - ItemNormaliser.WINDOW_MS), zone())
        val regional = source.region != Region.GLOBAL
        for (draft in DailyCap.apply(fresh, source.dailyCap, perDay, zone())) {
            if (TitleKey.isDedupable(draft.titleKey)) {
                val matches = stories.titleMatches(draft.titleKey, draft.publishedAt, TITLE_WINDOW_MS)
                if (matches.isNotEmpty()) {
                    // A regional copy beats a Global one whatever the fetch order; within the same scope the earlier
                    // first-seen copy stays. A saved copy is never replaced.
                    val replaceable = regional && matches.all { it.region == Region.GLOBAL.name && it.savedAt == null }
                    if (!replaceable) continue
                    matches.forEach { stories.deleteUnsaved(it.id) }
                }
            }
            val tags = Tagger.tag(draft.title, draft.snippet, source)
            stories.insert(
                StoryEntity(
                    sourceId = source.id, sourceName = source.name, url = draft.url, urlKey = draft.urlKey,
                    title = draft.title, titleKey = draft.titleKey, snippet = draft.snippet, imageUrl = draft.imageUrl,
                    publishedAt = draft.publishedAt, firstSeenAt = now,
                    lenses = tags.lenses, sectors = tags.sectors, regionMentions = tags.regionMentions,
                ),
            )
        }
    }

    /** NFR-004: 14 days and at most 2,000 unsaved stories; saved stories are never pruned (D-023 G-5). */
    suspend fun prune(now: Long) = db.withTransaction {
        db.stories().pruneOlderThan(now - ItemNormaliser.WINDOW_MS)
        db.stories().pruneUnsavedBeyond(MAX_UNSAVED)
    }

    companion object {
        const val PARALLEL = 6
        const val MAX_UNSAVED = 2_000
        const val TITLE_WINDOW_MS = 48L * 60 * 60 * 1000
        const val STATUS_OK = "OK"
        const val STATUS_NOT_MODIFIED = "NOT_MODIFIED"
    }
}
