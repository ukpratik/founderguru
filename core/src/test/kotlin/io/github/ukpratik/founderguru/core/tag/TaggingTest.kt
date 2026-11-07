// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.tag

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.ukpratik.founderguru.core.model.Lens
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.SourceDef
import org.junit.Test

class WordMatcherTest {
    @Test fun `whole words with explicit stems`() {
        val m = WordMatcher(listOf("funds?", "rais(?:e|es|ed|ing)"), listOf("AI"))
        assertThat(m.matches("New fund for founders")).isTrue()
        assertThat(m.matches("Zepto raised money")).isTrue()
        assertThat(m.matches("A fundamental shift")).isFalse()
        assertThat(m.matches("Fundraiser dinner")).isFalse()
        assertThat(m.matches("She said no")).isFalse()
        assertThat(m.matches("Paid ads")).isFalse()
        assertThat(m.matches("AI-first startup")).isTrue()
        assertThat(m.matches("ai startup")).isFalse() // acronyms are case-sensitive
    }

    @Test fun `unicode letters are word characters`() {
        assertThat(WordMatcher(listOf("café")).matches("Le cafés")).isFalse()
        assertThat(WordMatcher(emptyList(), listOf("India")).matches("Indiana startup")).isFalse()
    }
}

class LensTaggerTest {
    private val plain = SourceDef("x", "X", Region.IN, "https://x.com/f", "https://x.com")

    private fun lenses(title: String, source: SourceDef = plain) = Lens.entries.filter { Tagger.lenses(title, "", source) and it.bit != 0 }

    @Test fun `keyword rules`() {
        assertThat(lenses("Zepto raises \$450M in Series G")).containsExactly(Lens.FUNDING)
        assertThat(lenses("Razorpay files for IPO")).containsExactly(Lens.FUNDING)
        assertThat(lenses("Cashfree launches checkout for agents")).containsExactly(Lens.LAUNCHES)
        assertThat(lenses("Show HN: a tiny database")).containsExactly(Lens.LAUNCHES)
        assertThat(lenses("Edtech firm lays off 200")).containsExactly(Lens.TRENDS)
        assertThat(lenses("How to run your first board meeting")).containsExactly(Lens.PLAYBOOKS)
        assertThat(lenses("A fundamental rethink of chips")).isEmpty()
    }

    @Test fun `source default lens is always added, keywords add more`() {
        val ph = plain.copy(defaultLens = Lens.LAUNCHES)
        assertThat(lenses("A calendar for teams", ph)).containsExactly(Lens.LAUNCHES)
        assertThat(lenses("Calendar app raises seed round", ph)).containsExactly(Lens.LAUNCHES, Lens.FUNDING)
    }

    @Test fun `startup filter keeps startup stories and drops general news`() {
        assertThat(StartupFilter.keep("Fintech soonicorn Cashfree expands")).isTrue()
        assertThat(StartupFilter.keep("Founder of Zepto speaks")).isTrue()
        assertThat(StartupFilter.keep("Peppermint Robotics raises Rs 100 crore")).isTrue()
        assertThat(StartupFilter.keep("Lenskart Q2 net profit more than doubles")).isFalse()
        assertThat(StartupFilter.keep("Monsoon arrives early in Kerala")).isFalse()
    }
}

class SectorMatcherTest {
    private fun sectors(t: String) = Sector.fromMask(SectorMatcher.sectors(t))

    @Test fun `acronyms are case-sensitive whole words`() {
        assertThat(sectors("AI startup")).contains(Sector.AI)
        assertThat(sectors("She said hello")).doesNotContain(Sector.AI)
        assertThat(sectors("EV maker expands")).contains(Sector.MOBILITY_EV)
        assertThat(sectors("Every evening")).doesNotContain(Sector.MOBILITY_EV)
        assertThat(sectors("SaaS for dentists")).contains(Sector.SAAS_B2B)
        assertThat(sectors("D2C brand grows")).contains(Sector.D2C_ECOMMERCE)
    }

    @Test fun `several sectors per story`() {
        assertThat(sectors("Agritech startup uses AI drones on farms")).containsAtLeast(Sector.AGRITECH, Sector.AI, Sector.DEEPTECH_SEMIS)
    }

    @Test fun `ordinals are append-only`() {
        // Stored as bits: these must never change (LLD §2.1).
        assertThat(Sector.entries.size).isEqualTo(20)
        assertThat(Sector.FINTECH.ordinal).isEqualTo(0)
        assertThat(Sector.FOOD_QUICK_COMMERCE.ordinal).isEqualTo(19)
    }
}

class GazetteerTest {
    private fun regions(t: String) = Region.entries.filter { Gazetteer.mentions(t) and it.mentionBit != 0 && it != Region.GLOBAL }

    @Test fun table() {
        val cases = listOf(
            "Bengaluru startup raises" to listOf(Region.IN),
            "Indiana startup raises" to emptyList(),
            "Georgia passes a new law" to emptyList(),
            "New Mexico fund backs founders" to emptyList(),
            "Mexico City fintech raises" to listOf(Region.LATAM),
            "New England biotech" to emptyList(),
            "Scaling in England" to listOf(Region.EU),
            "She said the AI market grew" to emptyList(),
            "US startups raised \$20B" to listOf(Region.NA),
            "U.S. blocks DJI" to listOf(Region.NA),
            "BUILT FOR US BY FOUNDERS" to emptyList(),
            "join us today" to emptyList(),
            "Nigeria's Helium Health and Kenya's PayServe" to listOf(Region.AF),
            "Côte d'Ivoire fintech" to listOf(Region.AF),
            "Singapore and São Paulo" to listOf(Region.SEA, Region.LATAM),
            "London and Toronto" to listOf(Region.EU, Region.NA),
            "Turkey's Getir; Jordan's Mawdoo3; Chad and Niger" to emptyList(),
        )
        for ((text, expected) in cases) assertWithMessage(text).that(regions(text)).containsExactlyElementsIn(expected)
    }

    @Test fun `region mentions are set only for Global sources`() {
        val global = SourceDef("g", "G", Region.GLOBAL, "https://g.com/f", "https://g.com")
        assertThat(Tagger.tag("Indian startup raises", "", global).regionMentions).isEqualTo(Region.IN.mentionBit)
        assertThat(Tagger.tag("Indian startup raises", "", global.copy(region = Region.IN)).regionMentions).isEqualTo(0)
    }
}
