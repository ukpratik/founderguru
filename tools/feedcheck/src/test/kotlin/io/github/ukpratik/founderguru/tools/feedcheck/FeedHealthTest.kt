// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.tools.feedcheck

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.model.FailureKind
import io.github.ukpratik.founderguru.core.model.FetchOutcome
import io.github.ukpratik.founderguru.core.model.ParsedItem
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.SourceDef
import io.github.ukpratik.founderguru.tools.feedcheck.SourceHealth.Status
import org.junit.Test

class FeedHealthTest {
    private val now = 1_794_000_000_000L // 2026-11-07
    private val day = 86_400_000L
    private val src = SourceDef("s", "Source", Region.IN, "https://s.example/feed", "https://s.example", staleAfterDays = 14)

    private fun rfc822(ms: Long) = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
        .format(java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC))

    private fun updated(vararg ages: Long) = FetchOutcome.Updated(
        ages.map { ParsedItem(title = "t", link = "https://s.example/a", published = rfc822(now - it * day)) },
        null, null, null, "https://s.example/feed", false,
    )

    @Test fun `fresh items are ok`() {
        val h = FeedHealth.classify(src, updated(1, 3), now)
        assertThat(h.status).isEqualTo(Status.OK)
        assertThat(h.items).isEqualTo(2)
        assertThat(h.newestAgeDays).isEqualTo(1)
    }

    @Test fun `stale by staleAfterDays is broken, the limit itself is ok`() {
        assertThat(FeedHealth.classify(src, updated(15, 30), now).status).isEqualTo(Status.BROKEN)
        assertThat(FeedHealth.classify(src, updated(14), now).status).isEqualTo(Status.OK)
        assertThat(FeedHealth.classify(src.copy(staleAfterDays = 60), updated(30), now).status).isEqualTo(Status.OK)
    }

    @Test fun `zero items, parse errors and timeouts are broken`() {
        assertThat(FeedHealth.classify(src, updated(), now).reason).isEqualTo("0 items")
        assertThat(FeedHealth.classify(src, FetchOutcome.Failed(FailureKind.PARSE, null, "bad xml"), now).status).isEqualTo(Status.BROKEN)
        assertThat(FeedHealth.classify(src, FetchOutcome.Failed(FailureKind.HTTP, 522, "HTTP 522"), now).reason).startsWith("HTTP 522")
        assertThat(FeedHealth.classify(src, FetchOutcome.Failed(FailureKind.TIMEOUT, null, "timed out"), now).status).isEqualTo(Status.BROKEN)
    }

    @Test fun `403 and other publisher blocks are blocked-from-CI, never broken`() {
        val h403 = FeedHealth.classify(src, FetchOutcome.Failed(FailureKind.BLOCKED, 403, "HTTP 403"), now)
        assertThat(h403.status).isEqualTo(Status.BLOCKED_FROM_CI)
        assertThat(h403.reason).contains("confirm on a device")
        assertThat(FeedHealth.classify(src, FetchOutcome.Failed(FailureKind.BLOCKED, 503, "challenge"), now).status).isEqualTo(Status.BLOCKED_FROM_CI)
        assertThat(FeedHealth.classify(src, FetchOutcome.Failed(FailureKind.HTTP, 403, "HTTP 403"), now).status).isEqualTo(Status.BLOCKED_FROM_CI)
    }

    @Test fun `a 304 without conditional headers is reported`() {
        assertThat(FeedHealth.classify(src, FetchOutcome.NotModified, now).status).isEqualTo(Status.BROKEN)
    }

    @Test fun `markdown lists every source with counts`() {
        val md = FeedHealth.markdown(listOf(
            FeedHealth.classify(src, updated(1), now),
            FeedHealth.classify(src.copy(id = "b"), FetchOutcome.Failed(FailureKind.BLOCKED, 403, "HTTP 403"), now),
        ))
        assertThat(md).startsWith("Feed health: 1 ok, 0 broken, 1 blocked from CI, of 2")
        assertThat(md).contains("| `b` | BLOCKED_FROM_CI |")
    }
}
