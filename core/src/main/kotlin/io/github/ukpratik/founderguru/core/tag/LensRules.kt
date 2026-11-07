// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.tag

import io.github.ukpratik.founderguru.core.model.Lens
import io.github.ukpratik.founderguru.core.model.SourceDef

/**
 * Keyword rules per founder lens (US-005, ADR-005, LLD §4.6). The starting set is the LLD table; any change is measured
 * with LensPrecisionTest on the frozen `lens-testset.json` and `lens-holdout.json` (precision ≥ 0.75 per lens on both)
 * and bumps [Tagger.TAGGER_VERSION]. Rules are tuned on `lens-testset.json` only; the held-out set only measures (D-024).
 */
object LensRules {
    val rules: Map<Lens, WordMatcher> = mapOf(
        Lens.FUNDING to WordMatcher(
            caseInsensitive = listOf(
                "rais(?:e|es|ed|ing)", "funding", "funds?", "fundrais(?:e|es|ed|ing)", "seed round", "pre-seed", "seed funding",
                "series [a-f] round", "valuation", "acquir(?:e|es|ed)", "acquisition", "merger", "backs",
                "invest(?:s|ed|ment)? in", "debt financing", "new fund", "closes \\$",
            ),
            caseSensitive = listOf("IPO", "Series [A-F]", "VC fund"),
        ),
        Lens.LAUNCHES to WordMatcher(
            caseInsensitive = listOf(
                "launch(?:es|ed|ing)?", "unveil(?:s|ed)?", "introduc(?:es|ed)", "debut(?:s|ed)?", "rolls out", "now available",
                "open-sourc(?:es|ed)", "beta",
            ),
            caseSensitive = listOf("Show HN"),
            // D-024: a fund launch is Funding (label guide), not a product launch.
            exclude = listOf("launch(?:es|ed|ing)?[^.]{0,40}?(?<![\\p{L}\\p{N}])funds?", "funds?[^.]{0,20}?launched"),
        ),
        Lens.TRENDS to WordMatcher(
            caseInsensitive = listOf(
                // Layoffs, shutdowns, jobs
                "layoffs?", "lays off", "laid off", "job cuts", "retrenchments?", "unemployment", "shuts down", "shutter(?:s|ed)?",
                "shutdown", "closures?", "defunct",
                // Regulation and policy (D-024 adds the policy vocabulary)
                "regulations?", "regulators?", "regulatory", "antitrust", "bans?", "banned", "tariffs?", "polic(?:y|ies)", "laws?",
                "legislation", "lawmakers", "governments?", "ministers?", "ministry", "budget", "taxes",
                "tax (?:incentives?|breaks?|credits?|rules?|cuts?)", "fraud", "crackdown",
                // Market data and reports (D-024 adds funding-data phrasing)
                "funding winter", "slowdown", "report (?:finds|shows|says)", "(?:study|survey|index) (?:finds|shows|says)",
                "data shows", "per \\w+ data", "according to (?:a |the )?(?:new |latest )?(?:report|data|study|survey)",
                "market share", "venture funding", "(?:weekly|monthly|quarterly) funding", "funding (?:roundup|tracker|data)",
                "deals digest", "dealmaking",
                // Analysis framing (D-024)
                "trends?", "why", "the future of", "the rise of", "to watch", "experts?", "challenges",
            ),
        ),
        Lens.PLAYBOOKS to WordMatcher(
            caseInsensitive = listOf(
                "how to", "how we", "lessons?", "playbook", "advice", "guide to", "founder['’]s guide", "what I learned",
                "mistakes", "framework for", "tips for",
            ),
        ),
    )

    /** Lenses found by keywords alone (no source default). */
    fun keywordLenses(text: String): Int = rules.entries.fold(0) { acc, (lens, m) -> if (m.matches(text)) acc or lens.bit else acc }
}

/** `lenses = bit(source.defaultLens) | keywordLenses(text)` (LLD §4.6). 0 = shown under All only. */
object LensTagger {
    fun tag(text: String, source: SourceDef): Int = (source.defaultLens?.bit ?: 0) or LensRules.keywordLenses(text)
}

/**
 * Keyword filter for general feeds (`needsKeywordFilter`, LLD §4.6): keep an item when it matches any keyword lens
 * (not the source default) or a startup word.
 */
object StartupFilter {
    private val startupWords = WordMatcher(
        caseInsensitive = listOf(
            "startups?", "founders?", "co-founders?", "cofounders?", "venture", "investors?", "unicorns?", "soonicorns?",
            "accelerators?", "incubators?", "fintech",
        ),
        caseSensitive = listOf("SaaS", "D2C"),
    )

    fun keep(text: String): Boolean = LensRules.keywordLenses(text) != 0 || startupWords.matches(text)
}
