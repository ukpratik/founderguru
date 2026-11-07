// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.fetch

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Network rules for feed fetching (LLD §4.2, US-004, D-006). One shared client: no cache, no cookies, redirects
 * followed by hand, an honest User-Agent.
 */
data class HttpPolicy(
    val userAgent: String,
    val callTimeoutMs: Long = 10_000,
    val maxRedirects: Int = 5,
    val maxBodyBytes: Long = 3L * 1024 * 1024,
    val maxItems: Int = 50,
) {
    fun newClient(): OkHttpClient = OkHttpClient.Builder()
        .callTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
        .connectTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(callTimeoutMs, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build()) }
        .build()

    companion object {
        const val ACCEPT = "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.8, */*;q=0.5"

        /** `FounderGuru/<versionName> (+<repo URL>)` (US-004). No browser spoofing (D-006, B-027). */
        fun userAgent(versionName: String): String = "FounderGuru/$versionName (+https://github.com/ukpratik/founderguru)"
    }
}
