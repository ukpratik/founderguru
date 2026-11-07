// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.tools.feedcheck

import io.github.ukpratik.founderguru.core.model.FailureKind
import io.github.ukpratik.founderguru.core.model.FetchOutcome
import io.github.ukpratik.founderguru.core.model.SourceDef
import io.github.ukpratik.founderguru.core.parse.FeedDates
import kotlinx.serialization.Serializable

/**
 * Health of one source after a run (HLD §8, LLD §8, NFR-014).
 * - [Status.OK]: items, newest within `staleAfterDays`.
 * - [Status.BROKEN]: a final failure, 0 items, or a newest item older than `staleAfterDays`. Opens or updates an issue.
 * - [Status.BLOCKED_FROM_CI]: the publisher refused the request (HTTP 403, 429 or a Cloudflare challenge). Publishers
 *   often block data-centre IPs such as GitHub's while phones are fine (HLD R-006), so this is reported but never
 *   opens an issue or fails the run; confirm on a device.
 */
@Serializable
data class SourceHealth(
    val id: String,
    val name: String,
    val status: Status,
    val reason: String,
    val items: Int = 0,
    val newestAgeDays: Int? = null,
    val attempts: Int = 1,
) {
    @Serializable
    enum class Status { OK, BROKEN, BLOCKED_FROM_CI }
}

object FeedHealth {
    private const val DAY_MS = 86_400_000L

    fun classify(source: SourceDef, outcome: FetchOutcome, now: Long, attempts: Int = 1): SourceHealth {
        fun h(status: SourceHealth.Status, reason: String, items: Int = 0, age: Int? = null) =
            SourceHealth(source.id, source.name, status, reason, items, age, attempts)
        return when (outcome) {
            is FetchOutcome.Failed -> when {
                outcome.kind == FailureKind.BLOCKED || outcome.httpCode == 403 ->
                    h(SourceHealth.Status.BLOCKED_FROM_CI, "blocked (HTTP ${outcome.httpCode ?: "?"}); may be CI-IP specific, confirm on a device")
                else -> h(SourceHealth.Status.BROKEN, "${outcome.kind}${outcome.httpCode?.let { " $it" } ?: ""}: ${outcome.message}")
            }
            // feedcheck never sends conditional headers, so a 304 is a publisher bug: report it.
            FetchOutcome.NotModified -> h(SourceHealth.Status.BROKEN, "304 without a conditional request")
            is FetchOutcome.Updated -> {
                val items = outcome.items.size
                if (items == 0) return h(SourceHealth.Status.BROKEN, "0 items")
                val newest = outcome.items.mapNotNull { FeedDates.parse(it.published) }.maxOrNull()
                val age = newest?.let { ((now - it).coerceAtLeast(0) / DAY_MS).toInt() }
                when {
                    age == null -> h(SourceHealth.Status.OK, "ok (no item dates)", items)
                    age > source.staleAfterDays -> h(SourceHealth.Status.BROKEN, "stale: newest item $age days old (limit ${source.staleAfterDays})", items, age)
                    else -> h(SourceHealth.Status.OK, "ok", items, age)
                }
            }
        }
    }

    /** Markdown summary for the job log and issue bodies. */
    fun markdown(results: List<SourceHealth>): String = buildString {
        val counts = results.groupingBy { it.status }.eachCount()
        appendLine("Feed health: ${counts[SourceHealth.Status.OK] ?: 0} ok, ${counts[SourceHealth.Status.BROKEN] ?: 0} broken, " +
            "${counts[SourceHealth.Status.BLOCKED_FROM_CI] ?: 0} blocked from CI, of ${results.size}")
        appendLine()
        appendLine("| Source | Status | Items | Newest (days) | Reason |")
        appendLine("|---|---|---|---|---|")
        for (r in results) appendLine("| `${r.id}` | ${r.status} | ${r.items} | ${r.newestAgeDays ?: ""} | ${r.reason.replace("|", "/")} |")
    }
}
