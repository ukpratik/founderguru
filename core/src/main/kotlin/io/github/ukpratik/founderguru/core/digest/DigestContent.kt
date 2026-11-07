// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.digest

import io.github.ukpratik.founderguru.core.feed.SectorCount
import io.github.ukpratik.founderguru.core.model.Lens
import io.github.ukpratik.founderguru.core.model.StoryRow

/** The digest notification text (design `notification.html`): title "This week in India", body the PRD string. */
data class DigestText(val title: String, val body: String, val newCount: Int)

/**
 * Digest copy (US-012, D-023 G-7, design notification notes). English only (LLD §9).
 * - Title: "This week in <region>" (weekly) / "Today in <region>" (daily); "worldwide" for Global only.
 * - Body: "<n> funding stories · <sector> is hot · <n> new stories", parts joined with " · ", plural-aware; the
 *   funding and hot parts are dropped when zero or empty.
 * - n = stories first seen since the last visit ([newRows]); none → null, and the notification is skipped.
 */
object DigestContent {
    /** [regionName] = the region's display name, or null for "Global only" ("worldwide"). */
    fun build(regionName: String?, mode: DigestMode, newRows: List<StoryRow>, hot: List<SectorCount>): DigestText? {
        if (newRows.isEmpty() || mode == DigestMode.OFF) return null
        val place = regionName?.let { "in $it" } ?: "worldwide"
        val title = if (mode == DigestMode.DAILY) "Today $place" else "This week $place"
        val funding = newRows.count { it.lenses and Lens.FUNDING.bit != 0 }
        val parts = buildList {
            if (funding > 0) add(plural(funding, "funding story", "funding stories"))
            hot.firstOrNull()?.let { add("${it.sector.displayName} is hot") }
            add(plural(newRows.size, "new story", "new stories"))
        }
        return DigestText(title, parts.joinToString(" · "), newRows.size)
    }

    /** Stories first seen after [lastVisit] (the count the tapped tab's divider shows, D-017). */
    fun newSince(rows: List<StoryRow>, lastVisit: Long): List<StoryRow> = rows.filter { it.firstSeenAt > lastVisit }

    private fun plural(n: Int, one: String, other: String) = "$n ${if (n == 1) one else other}"
}
