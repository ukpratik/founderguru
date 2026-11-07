// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.fetch

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.TestFeeds
import io.github.ukpratik.founderguru.core.model.FailureKind
import io.github.ukpratik.founderguru.core.model.FetchOutcome
import io.github.ukpratik.founderguru.core.model.HttpState
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.model.SourceDef
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class FeedFetcherTest {
    private val server = MockWebServer()
    private val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
    private var policy = HttpPolicy(userAgent = HttpPolicy.userAgent("1.2.3"), callTimeoutMs = 1_000)

    private val rss = """<?xml version="1.0"?><rss><channel><item><title>One</title><link>https://a.com/1</link></item></channel></rss>"""

    @Before fun setUp() {
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory())
        server.start()
    }

    @After fun tearDown() = server.close()

    private fun fetcher(): FeedFetcher {
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val client = policy.newClient().newBuilder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager).build()
        return FeedFetcher(client, TestFeeds.parserFactory, policy)
    }

    private fun source(path: String = "/feed") =
        SourceDef("s", "S", Region.GLOBAL, server.url(path).toString(), "https://a.com")

    private fun fetch(state: HttpState = HttpState(), path: String = "/feed") = runBlocking { fetcher().fetch(source(path), state) }

    private fun ok(body: String = rss) = MockResponse.Builder().code(200).body(body)

    @Test fun `200 parses and records ETag and Last-Modified, UA and Accept are sent`() {
        server.enqueue(ok().addHeader("ETag", "\"v1\"").addHeader("Last-Modified", "Fri, 06 Nov 2026 10:00:00 GMT").build())
        val out = fetch() as FetchOutcome.Updated
        assertThat(out.items.single().title).isEqualTo("One")
        assertThat(out.etag).isEqualTo("\"v1\"")
        assertThat(out.lastModified).isEqualTo("Fri, 06 Nov 2026 10:00:00 GMT")
        assertThat(out.permanentUrl).isNull()
        val req = server.takeRequest()
        assertThat(req.headers["User-Agent"]).isEqualTo("FounderGuru/1.2.3 (+https://github.com/ukpratik/founderguru)")
        assertThat(req.headers["Accept"]).startsWith("application/rss+xml")
        assertThat(req.headers["If-None-Match"]).isNull()
    }

    @Test fun `conditional headers are echoed and 304 is NotModified`() {
        server.enqueue(MockResponse.Builder().code(304).build())
        assertThat(fetch(HttpState(etag = "\"v1\"", lastModified = "Fri, 06 Nov 2026 10:00:00 GMT"))).isEqualTo(FetchOutcome.NotModified)
        val req = server.takeRequest()
        assertThat(req.headers["If-None-Match"]).isEqualTo("\"v1\"")
        assertThat(req.headers["If-Modified-Since"]).isEqualTo("Fri, 06 Nov 2026 10:00:00 GMT")
    }

    @Test fun `a stored final URL is requested instead of the asset URL`() {
        server.enqueue(ok().build())
        fetch(HttpState(finalUrl = server.url("/moved").toString()))
        assertThat(server.takeRequest().url.encodedPath).isEqualTo("/moved")
    }

    @Test fun `permanent redirect chain is reported, temporary is not`() {
        server.enqueue(MockResponse.Builder().code(301).addHeader("Location", "/b").build())
        server.enqueue(MockResponse.Builder().code(308).addHeader("Location", server.url("/c").toString()).build())
        server.enqueue(ok().build())
        assertThat((fetch() as FetchOutcome.Updated).permanentUrl).isEqualTo(server.url("/c").toString())

        server.enqueue(MockResponse.Builder().code(301).addHeader("Location", "/b").build())
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/c").build())
        server.enqueue(ok().build())
        val out = fetch() as FetchOutcome.Updated
        assertThat(out.permanentUrl).isNull()
        assertThat(out.fetchedUrl).isEqualTo(server.url("/c").toString())
    }

    @Test fun `more than 5 redirects fails with REDIRECTS`() {
        repeat(6) { server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/r$it").build()) }
        assertThat((fetch() as FetchOutcome.Failed).kind).isEqualTo(FailureKind.REDIRECTS)
    }

    @Test fun `exactly 5 redirects are followed`() {
        repeat(5) { server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/r$it").build()) }
        server.enqueue(ok().build())
        assertThat(fetch()).isInstanceOf(FetchOutcome.Updated::class.java)
    }

    @Test fun `redirect to http is INSECURE, and an http source is never requested`() {
        server.enqueue(MockResponse.Builder().code(301).addHeader("Location", "http://localhost:${server.port}/x").build())
        assertThat((fetch() as FetchOutcome.Failed).kind).isEqualTo(FailureKind.INSECURE)
        val http = SourceDef("s", "S", Region.GLOBAL, "http://example.com/feed", "https://a.com")
        assertThat((runBlocking { fetcher().fetch(http, HttpState()) } as FetchOutcome.Failed).kind).isEqualTo(FailureKind.INSECURE)
    }

    @Test fun `403, 429, 451 and a Cloudflare 503 are BLOCKED, other errors are HTTP`() {
        for (code in listOf(403, 429, 451)) {
            server.enqueue(MockResponse.Builder().code(code).build())
            assertThat((fetch() as FetchOutcome.Failed).kind).isEqualTo(FailureKind.BLOCKED)
        }
        server.enqueue(MockResponse.Builder().code(503).addHeader("cf-mitigated", "challenge").build())
        assertThat((fetch() as FetchOutcome.Failed).kind).isEqualTo(FailureKind.BLOCKED)
        server.enqueue(MockResponse.Builder().code(503).body("<html><title>Just a moment...</title></html>").build())
        assertThat((fetch() as FetchOutcome.Failed).kind).isEqualTo(FailureKind.BLOCKED)
        server.enqueue(MockResponse.Builder().code(503).body("maintenance").build())
        assertThat(fetch() as FetchOutcome.Failed).isEqualTo(FetchOutcome.Failed(FailureKind.HTTP, 503, "HTTP 503"))
        server.enqueue(MockResponse.Builder().code(404).build())
        assertThat((fetch() as FetchOutcome.Failed).kind).isEqualTo(FailureKind.HTTP)
    }

    @Test fun `an HTML page with 200 is NOT_A_FEED`() {
        server.enqueue(ok(String(TestFeeds.bytes("cloudflare-challenge.html"))).build())
        assertThat((fetch() as FetchOutcome.Failed).kind).isEqualTo(FailureKind.NOT_A_FEED)
    }

    @Test fun `a body that drips past the call timeout is TIMEOUT`() {
        server.enqueue(ok(rss + " ".repeat(200)).throttleBody(16, 300, TimeUnit.MILLISECONDS).build())
        assertThat((fetch() as FetchOutcome.Failed).kind).isEqualTo(FailureKind.TIMEOUT)
    }

    @Test fun `size cap - after an item is truncated, before any item is TOO_LARGE`() {
        policy = policy.copy(maxBodyBytes = 10_000, callTimeoutMs = 5_000)
        val padding = "<!--" + "x".repeat(20_000) + "-->"
        server.enqueue(ok("<rss><channel><item><title>One</title><link>https://a.com/1</link></item>$padding</channel></rss>").build())
        val out = fetch() as FetchOutcome.Updated
        assertThat(out.truncated).isTrue()
        assertThat(out.items).hasSize(1)

        server.enqueue(ok("<rss><channel>$padding<item><title>One</title></item></channel></rss>").build())
        assertThat((fetch() as FetchOutcome.Failed).kind).isEqualTo(FailureKind.TOO_LARGE)
    }

    @Test fun `gzip bodies are decoded`() {
        val gz = Buffer()
        GzipSink(gz).buffer().use { it.writeUtf8(rss) }
        server.enqueue(MockResponse.Builder().code(200).addHeader("Content-Encoding", "gzip").body(gz).build())
        assertThat((fetch() as FetchOutcome.Updated).items).hasSize(1)
    }

    @Test fun `parse error before any item is PARSE`() {
        server.enqueue(ok("<rss><channel><item><title>x</bad></rss>").build())
        assertThat((fetch() as FetchOutcome.Failed).kind).isEqualTo(FailureKind.PARSE)
    }

    @Test fun `connection refused is NETWORK`() {
        val url = server.url("/feed").toString()
        server.close()
        val out = runBlocking { fetcher().fetch(SourceDef("s", "S", Region.GLOBAL, url, "https://a.com"), HttpState()) }
        assertThat((out as FetchOutcome.Failed).kind).isAnyOf(FailureKind.NETWORK, FailureKind.TIMEOUT)
    }
}
