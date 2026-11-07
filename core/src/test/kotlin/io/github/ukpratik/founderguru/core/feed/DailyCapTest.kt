// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.feed

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.model.StoryDraft
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DailyCapTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun draft(n: Int, at: String) =
        StoryDraft("https://a/$n", "a/$n", "t$n", "t$n", "", null, Instant.parse(at).toEpochMilli())

    @Test fun `keeps the first cap items per local day in feed order`() {
        val drafts = (1..7).map { draft(it, "2026-11-06T10:00:00Z") } + draft(8, "2026-11-05T10:00:00Z")
        assertThat(DailyCap.apply(drafts, 5, emptyMap(), zone).map { it.urlKey })
            .containsExactly("a/1", "a/2", "a/3", "a/4", "a/5", "a/8").inOrder()
    }

    @Test fun `counts what is already stored`() {
        val drafts = (1..3).map { draft(it, "2026-11-06T10:00:00Z") }
        assertThat(DailyCap.apply(drafts, 5, mapOf(LocalDate.of(2026, 11, 6) to 4), zone)).hasSize(1)
    }

    @Test fun `uses the local day, not the UTC day`() {
        // 20:00Z on the 5th is 01:30 on the 6th in Kolkata.
        val drafts = listOf(draft(1, "2026-11-05T20:00:00Z"))
        assertThat(DailyCap.apply(drafts, 1, mapOf(LocalDate.of(2026, 11, 6) to 1), zone)).isEmpty()
        assertThat(DailyCap.perDay(listOf(Instant.parse("2026-11-05T20:00:00Z").toEpochMilli()), zone))
            .containsExactly(LocalDate.of(2026, 11, 6), 1)
    }

    @Test fun `no cap keeps everything`() {
        val drafts = (1..60).map { draft(it, "2026-11-06T10:00:00Z") }
        assertThat(DailyCap.apply(drafts, null, emptyMap(), zone)).hasSize(60)
    }
}
