// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.sources

import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.SourcesCatalog
import kotlinx.serialization.json.Json

/**
 * Decodes and validates `assets/sources.json` (LLD §2.3, NFR-011). An invalid asset is a programming error: it throws
 * [InvalidCatalogException], and CatalogLoaderTest checks the shipped file so that never reaches users.
 */
object CatalogLoader {
    const val SCHEMA_VERSION = 1

    /** Expected shape per list version; updated on purpose whenever the list changes. */
    private val expectedCounts = mapOf(1 to ExpectedCounts(global = 7, total = 24))

    private data class ExpectedCounts(val global: Int, val total: Int)

    private val ID = Regex("^[a-z0-9-]{2,40}$")

    private val lenient = Json { ignoreUnknownKeys = true }
    private val strict = Json { ignoreUnknownKeys = false }

    /** [strictKeys] = true in tests: unknown keys are errors there, but ignored at runtime. */
    fun load(json: String, strictKeys: Boolean = false): SourcesCatalog {
        val catalog = try {
            (if (strictKeys) strict else lenient).decodeFromString(SourcesCatalog.serializer(), json)
        } catch (e: IllegalArgumentException) {
            throw InvalidCatalogException("sources.json does not decode: ${e.message}", e)
        }
        val errors = validate(catalog)
        if (errors.isNotEmpty()) throw InvalidCatalogException(errors.joinToString("\n"))
        return catalog
    }

    fun validate(catalog: SourcesCatalog): List<String> = buildList {
        if (catalog.schemaVersion != SCHEMA_VERSION) add("schemaVersion ${catalog.schemaVersion} != $SCHEMA_VERSION")
        val seen = HashSet<String>()
        for (s in catalog.sources) {
            if (!ID.matches(s.id)) add("${s.id}: id must match ${ID.pattern}")
            if (!seen.add(s.id)) add("${s.id}: duplicate id")
            if (s.name.isBlank() || s.name.length > 40) add("${s.id}: name must be 1-40 characters")
            if (!s.url.startsWith("https://")) add("${s.id}: url must be https")
            if (!s.homepage.startsWith("https://")) add("${s.id}: homepage must be https")
            s.dailyCap?.let { if (it !in 1..50) add("${s.id}: dailyCap must be 1-50") }
            if (s.staleAfterDays !in 1..365) add("${s.id}: staleAfterDays must be 1-365")
        }
        val expected = expectedCounts[catalog.listVersion]
        if (expected == null) {
            add("listVersion ${catalog.listVersion} has no expected counts")
        } else {
            val global = catalog.sources.count { it.region == Region.GLOBAL }
            if (global != expected.global) add("expected ${expected.global} GLOBAL sources, found $global")
            if (catalog.sources.size != expected.total) add("expected ${expected.total} sources, found ${catalog.sources.size}")
        }
        for (region in Region.entries - Region.GLOBAL) {
            val n = catalog.sources.count { it.region == region }
            if (n < 2) add("region $region needs at least 2 sources, has $n")
        }
    }
}

class InvalidCatalogException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
