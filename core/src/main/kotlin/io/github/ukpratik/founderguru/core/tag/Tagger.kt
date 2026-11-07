// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.tag

import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.SourceDef

data class Tags(val lenses: Int, val sectors: Long, val regionMentions: Int)

/**
 * All tagging for one story (LLD §3, §4.6–4.8). Bump [TAGGER_VERSION] whenever a rule list changes, so stored stories
 * are re-tagged at the next start (LLD §4.10).
 */
object Tagger {
    const val TAGGER_VERSION = 1

    fun tag(title: String, snippet: String, source: SourceDef): Tags {
        val text = WordMatcher.text(title, snippet)
        return Tags(
            lenses = LensTagger.tag(text, source),
            sectors = SectorMatcher.sectors(text),
            regionMentions = if (source.region == Region.GLOBAL) Gazetteer.mentions(text) else 0,
        )
    }

    fun lenses(title: String, snippet: String, source: SourceDef): Int = LensTagger.tag(WordMatcher.text(title, snippet), source)
}
