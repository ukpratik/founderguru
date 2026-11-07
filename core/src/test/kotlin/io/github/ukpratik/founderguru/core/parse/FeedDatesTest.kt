// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.parse

import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.time.Instant

class FeedDatesTest {
    private fun iso(s: String) = Instant.parse(s).toEpochMilli()

    @Test fun table() {
        val cases = listOf(
            "Fri, 06 Nov 2026 10:15:00 +0000" to "2026-11-06T10:15:00Z",
            "Fri, 06 Nov 2026 10:15:00 GMT" to "2026-11-06T10:15:00Z",
            "Fri,  6 Nov 2026 10:15 GMT" to "2026-11-06T10:15:00Z",
            "6 Nov 2026 10:15:00 Z" to "2026-11-06T10:15:00Z",
            "Thurs, 5 Nov 2026 15:35:00 IST" to "2026-11-05T10:05:00Z",
            "Fri, 06 Nov 26 10:15:00 EST" to "2026-11-06T15:15:00Z",
            "Fri, 06 Nov 2026 10:15:00 PDT" to "2026-11-06T17:15:00Z",
            "Fri, 06 Nov 2026 10:15:00 +05:30" to "2026-11-06T04:45:00Z",
            "Fri, 06 Sept 2026 10:15:00 CEST" to "2026-09-06T08:15:00Z",
            "Fri, 06 Nov 2026 10:15:00" to "2026-11-06T10:15:00Z",
            "Fri, 06 Nov 2026 10:15:00 WAT" to "2026-11-06T09:15:00Z",
            "2026-11-06T10:15:00Z" to "2026-11-06T10:15:00Z",
            "2026-11-06T10:15:00.123+01:00" to "2026-11-06T09:15:00Z",
            "2026-11-06T10:15:00+0100" to "2026-11-06T09:15:00Z",
            "2026-11-06 10:15:00" to "2026-11-06T10:15:00Z",
            "2026-11-06" to "2026-11-06T00:00:00Z",
        )
        for ((raw, expected) in cases) assertWithMessage(raw).that(FeedDates.parse(raw)).isEqualTo(iso(expected))
    }

    @Test fun `unparseable dates are null`() {
        for (raw in listOf(null, "", "yesterday", "32 Nov 2026 10:00:00 GMT", "Fri, 06 Foo 2026 10:15:00 GMT", "2026-13-01", "06 Nov 2026 10:15 XYZ")) {
            assertWithMessage(raw.toString()).that(FeedDates.parse(raw)).isNull()
        }
    }
}
