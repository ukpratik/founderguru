// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.tag

/**
 * Whole-word matching for one rule list (LLD §4.5). [caseInsensitive] and [caseSensitive] are regex fragments (explicit
 * stems such as `rais(?:e|es|ed|ing)`, never prefix wildcards). Boundaries are Unicode-aware, so "fund" never matches
 * "fundamental" and "AI" never matches "said". Each list compiles once into one alternation.
 */
class WordMatcher(caseInsensitive: List<String>, caseSensitive: List<String> = emptyList()) {
    private val ci: Regex? = compile(caseInsensitive, setOf(RegexOption.IGNORE_CASE))
    private val cs: Regex? = compile(caseSensitive, emptySet())

    fun matches(text: String): Boolean = ci?.containsMatchIn(text) == true || cs?.containsMatchIn(text) == true

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
