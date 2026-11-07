// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.normalise

import java.text.Normalizer
import java.util.Locale

/** The normalised title used for cross-source de-duplication (LLD §4.4). */
object TitleKey {
    /** Titles with fewer words are too generic ("Weekly funding roundup") to de-duplicate on. */
    const val MIN_WORDS = 4

    private val MARKS = Regex("\\p{M}+")
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")

    fun of(title: String): String {
        val lower = title.lowercase(Locale.ROOT)
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFKD).replace(MARKS, "")
        return stripped.replace(NON_ALNUM, " ").trim()
    }

    fun isDedupable(key: String): Boolean = key.isNotEmpty() && key.split(' ').size >= MIN_WORDS
}
