// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.normalise

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Article URL cleaning and the de-duplication key (LLD §4.4, US-004, D-017). */
object UrlNormaliser {
    const val MAX_URL = 2048

    /**
     * Resolves [link] against [base], keeps http(s) only, and removes tracking parameters (`utm_*`, `fbclid`, `ref`;
     * names matched case-insensitively). Null = drop the item.
     */
    fun clean(link: String, base: String?): String? {
        val trimmed = link.trim()
        if (trimmed.isEmpty()) return null
        val url = base?.toHttpUrlOrNull()?.resolve(trimmed) ?: trimmed.toHttpUrlOrNull() ?: return null
        return stripTracking(url).toString().takeIf { it.length <= MAX_URL }
    }

    /**
     * `urlKey`: host lowercased without a leading `www.`, scheme and default port dropped, fragment dropped, remaining
     * query kept in order, trailing `/` dropped (except the root). `https://www.inc42.com/buzz/x/?utm_source=rss#c` →
     * `inc42.com/buzz/x`.
     */
    fun key(url: String): String? {
        val u = url.toHttpUrlOrNull() ?: return null
        val clean = stripTracking(u)
        val host = clean.host.lowercase().removePrefix("www.")
        val port = if (clean.port != HttpUrl.defaultPort(clean.scheme)) ":${clean.port}" else ""
        var path = clean.encodedPath
        if (path.length > 1 && path.endsWith("/")) path = path.trimEnd('/').ifEmpty { "/" }
        val query = clean.encodedQuery?.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: ""
        return host + port + path + query
    }

    private fun stripTracking(url: HttpUrl): HttpUrl {
        val query = url.encodedQuery
        val kept = query?.split('&')?.filter { pair ->
            if (pair.isEmpty()) return@filter false
            val name = pair.substringBefore('=').lowercase()
            !(name.startsWith("utm_") || name == "fbclid" || name == "ref")
        }
        return url.newBuilder()
            .encodedQuery(kept?.takeIf { it.isNotEmpty() }?.joinToString("&"))
            .build()
    }
}
