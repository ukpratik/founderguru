// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.digest

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.feed.SectorCount
import io.github.ukpratik.founderguru.core.model.Lens
import io.github.ukpratik.founderguru.core.model.StoryRow
import io.github.ukpratik.founderguru.core.tag.Sector
import org.junit.Test

/** Digest copy (US-012, D-023 G-7, design notification notes): zero parts dropped, n = 0 skips, plurals. */
class DigestContentTest {
    private fun row(id: Long, firstSeenAt: Long = 100, funding: Boolean = false) = StoryRow(
        id = id, sourceId = "s", sourceName = "S", url = "https://e.com/$id", title = "T$id", snippet = "", imageUrl = null,
        publishedAt = firstSeenAt, firstSeenAt = firstSeenAt, lenses = if (funding) Lens.FUNDING.bit else 0, sectors = 0,
        regionMentions = 0, savedAt = null, openedAt = null,
    )

    private val hot = listOf(SectorCount(Sector.FINTECH, 14), SectorCount(Sector.AI, 11))

    @Test fun `weekly with every part`() {
        val rows = (1L..12).map { row(it, funding = true) } + (13L..41).map { row(it) }
        val text = DigestContent.build("India", DigestMode.WEEKLY, rows, hot)!!
        assertThat(text.title).isEqualTo("This week in India")
        assertThat(text.body).isEqualTo("12 funding stories · Fintech is hot · 41 new stories")
        assertThat(text.newCount).isEqualTo(41)
    }

    @Test fun `daily says Today`() {
        val text = DigestContent.build("Europe", DigestMode.DAILY, listOf(row(1, funding = true), row(2)), hot)!!
        assertThat(text.title).isEqualTo("Today in Europe")
        assertThat(text.body).isEqualTo("1 funding story · Fintech is hot · 2 new stories")
    }

    @Test fun `global only says worldwide`() {
        assertThat(DigestContent.build(null, DigestMode.WEEKLY, listOf(row(1)), hot)!!.title).isEqualTo("This week worldwide")
        assertThat(DigestContent.build(null, DigestMode.DAILY, listOf(row(1)), hot)!!.title).isEqualTo("Today worldwide")
    }

    @Test fun `zero funding and no hot sector are dropped`() {
        val text = DigestContent.build("Latin America", DigestMode.WEEKLY, (1L..4).map { row(it) }, emptyList())!!
        assertThat(text.body).isEqualTo("4 new stories")
        assertThat(DigestContent.build("Africa", DigestMode.WEEKLY, listOf(row(1)), emptyList())!!.body).isEqualTo("1 new story")
    }

    @Test fun `nothing new skips the notification`() {
        assertThat(DigestContent.build("India", DigestMode.WEEKLY, emptyList(), hot)).isNull()
        assertThat(DigestContent.build("India", DigestMode.OFF, listOf(row(1)), hot)).isNull()
    }

    @Test fun `new since uses firstSeenAt strictly after the last visit`() {
        val rows = listOf(row(1, firstSeenAt = 99), row(2, firstSeenAt = 100), row(3, firstSeenAt = 101))
        assertThat(DigestContent.newSince(rows, 100).map { it.id }).containsExactly(3L)
    }
}
