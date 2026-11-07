// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.tag

/**
 * Whole-word matching for one rule list (LLD §4.5). [caseInsensitive] and [caseSensitive] are regex fragments (explicit
 * stems such as `rais(?:e|es|ed|ing)`, never prefix wildcards). Boundaries are Unicode-aware, so "fund" never matches
 * "fundamental" and "AI" never matches "said". Each list compiles once into one alternation. [exclude] phrases
 * (case-insensitive, whole-word) are blanked out before matching, for uses of a keyword that mean something else
 * ("launches a fund" is Funding, not Launches; D-024).
 */
class WordMatcher(
    caseInsensitive: List<String>,
    caseSensitive: List<String> = emptyList(),
    exclude: List<String> = emptyList(),
) {
    private val ci: Regex? = compile(caseInsensitive, setOf(RegexOption.IGNORE_CASE))
    private val cs: Regex? = compile(caseSensitive, emptySet())
    private val ex: Regex? = compile(exclude, setOf(RegexOption.IGNORE_CASE))

    fun matches(text: String): Boolean {
        val t = ex?.replace(text, " ") ?: text
        return ci?.containsMatchIn(t) == true || cs?.containsMatchIn(t) == true
    }

    private fun compile(parts: List<String>, options: Set<RegexOption>): Regex? {
        if (parts.isEmpty()) return null
        // Longest first, so a phrase wins over its prefix within the alternation.
        val alternation = parts.sortedByDescending { it.length }.joinToString("|")
        return Regex("$BEFORE(?:$alternation)$AFTER", options)
    }

    companion object {
        private const val BEFORE = "(?<![\\p{L}\\p{N}])"
        private const val AFTER = "(?![\\p{L}\\p{N}])"

        /** Title and snippet, the text every rule runs on (LLD §4.5). */
        fun text(title: String, snippet: String): String = "$title $snippet"
    }
}
