// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.fetch

import io.github.ukpratik.founderguru.core.model.FailureKind
import io.github.ukpratik.founderguru.core.model.FetchOutcome
import io.github.ukpratik.founderguru.core.model.HttpState
import io.github.ukpratik.founderguru.core.model.SourceDef
import io.github.ukpratik.founderguru.core.parse.CharsetSniffer
import io.github.ukpratik.founderguru.core.parse.CleaningReader
import io.github.ukpratik.founderguru.core.parse.FeedParser
import io.github.ukpratik.founderguru.core.parse.ParseResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.InterruptedIOException

/**
 * Fetches and parses one feed (LLD §4.2, US-004, NFR-003/005). Never throws except [kotlinx.coroutines.CancellationException]:
 * every failure is a typed [FetchOutcome.Failed].
 */
class FeedFetcher(
    private val client: OkHttpClient,
    private val parserFactory: () -> XmlPullParser,
    private val policy: HttpPolicy,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun fetch(source: SourceDef, state: HttpState): FetchOutcome = withContext(io) {
        try {
            fetchBlocking(source, state)
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive() // a cancelled call surfaces as IOException: rethrow as cancellation
            when (e) {
                is InterruptedIOException -> FetchOutcome.Failed(FailureKind.TIMEOUT, null, "timed out")
                else -> FetchOutcome.Failed(FailureKind.NETWORK, null, e.javaClass.simpleName + ": " + e.message.orEmpty().take(150))
            }
        }
    }

    private suspend fun fetchBlocking(source: SourceDef, state: HttpState): FetchOutcome {
        var url: HttpUrl = (state.finalUrl ?: source.url).toHttpUrlOrNull()
            ?: return FetchOutcome.Failed(FailureKind.INSECURE, null, "not an http(s) URL")
        if (!url.isHttps) return FetchOutcome.Failed(FailureKind.INSECURE, null, "not https")
        var allPermanent = true
        var hops = 0
        while (true) {
            val request = Request.Builder().url(url).header("Accept", HttpPolicy.ACCEPT).apply {
                state.etag?.let { header("If-None-Match", it) }
                state.lastModified?.let { header("If-Modified-Since", it) }
            }.build()
            val call = client.newCall(request)
            val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
            try {
                call.execute().use { response ->
                    val code = response.code
                    if (code in REDIRECT_CODES) {
                        val location = response.header("Location")
                            ?: return FetchOutcome.Failed(FailureKind.HTTP, code, "redirect without Location")
                        val next = url.resolve(location)
                            ?: return FetchOutcome.Failed(FailureKind.INSECURE, code, "redirect to a non-http(s) URL")
                        if (!next.isHttps) return FetchOutcome.Failed(FailureKind.INSECURE, code, "redirect to http")
                        if (++hops > policy.maxRedirects) return FetchOutcome.Failed(FailureKind.REDIRECTS, code, "too many redirects")
                        allPermanent = allPermanent && (code == 301 || code == 308)
                        url = next
                        return@use null
                    }
                    return handleFinal(response, permanentUrl = if (hops > 0 && allPermanent) url.toString() else null)
                } ?: continue
            } finally {
                cancelHandle?.dispose()
            }
        }
    }

    private fun handleFinal(response: Response, permanentUrl: String?): FetchOutcome {
        val code = response.code
        when {
            code == 304 -> return FetchOutcome.NotModified
            code in BLOCKED_CODES -> return FetchOutcome.Failed(FailureKind.BLOCKED, code, "HTTP $code")
            code == 503 && looksLikeChallenge(response) -> return FetchOutcome.Failed(FailureKind.BLOCKED, code, "HTTP 503 challenge")
            code !in 200..299 -> return FetchOutcome.Failed(FailureKind.HTTP, code, "HTTP $code")
        }
        val body = response.body
        val counted = CountingInputStream(body.byteStream(), policy.maxBodyBytes)
        val reader = CleaningReader(CharsetSniffer.reader(counted, body.contentType()?.charset()))
        return when (val parsed = FeedParser(parserFactory, policy.maxItems).parse(reader)) {
            is ParseResult.Feed -> FetchOutcome.Updated(
                items = parsed.items,
                etag = response.header("ETag"),
                lastModified = response.header("Last-Modified"),
                permanentUrl = permanentUrl,
                fetchedUrl = response.request.url.toString(),
                truncated = parsed.truncated,
            )
            ParseResult.NotAFeed -> FetchOutcome.Failed(FailureKind.NOT_A_FEED, code, "not an RSS or Atom document")
            ParseResult.TooLarge -> FetchOutcome.Failed(FailureKind.TOO_LARGE, code, "larger than ${policy.maxBodyBytes} bytes")
            is ParseResult.Failed -> FetchOutcome.Failed(FailureKind.PARSE, code, parsed.message)
        }
    }

    private fun looksLikeChallenge(response: Response): Boolean {
        if (response.header("cf-mitigated") != null) return true
        val head = response.peekBody(CHALLENGE_PEEK).string()
        return CHALLENGE_MARKERS.any { head.contains(it, ignoreCase = true) }
    }

    private companion object {
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        val BLOCKED_CODES = setOf(403, 429, 451)
        const val CHALLENGE_PEEK = 8_192L
        val CHALLENGE_MARKERS = listOf("Just a moment", "cf-chl", "challenge-platform", "Attention Required")
    }
}
