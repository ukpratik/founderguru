// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.parse

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Picks the body charset (LLD §4.2): the `Content-Type` charset if present; otherwise a BOM or the XML declaration's
 * `encoding` within the first 1 KB; default UTF-8. Decoding never throws (malformed bytes become U+FFFD).
 */
object CharsetSniffer {
    private const val SNIFF = 1024
    private val DECL = Regex("""<\?xml[^>]*encoding\s*=\s*["']([A-Za-z0-9._:-]+)["']""")

    fun reader(input: InputStream, headerCharset: Charset?): Reader {
        val stream = if (input.markSupported()) input else BufferedInputStream(input, 8192)
        val charset = headerCharset ?: sniff(stream)
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        return InputStreamReader(stream, decoder)
    }

    /** Reads up to 1 KB ahead and resets, so the stream is unchanged. [stream] must support mark. */
    fun sniff(stream: InputStream): Charset {
        stream.mark(SNIFF)
        val head = ByteArray(SNIFF)
        var n = 0
        while (n < SNIFF) {
            val r = stream.read(head, n, SNIFF - n)
            if (r < 0) break
            n += r
        }
        stream.reset()
        if (n >= 3 && head[0] == 0xEF.toByte() && head[1] == 0xBB.toByte() && head[2] == 0xBF.toByte()) return Charsets.UTF_8
        if (n >= 2 && head[0] == 0xFE.toByte() && head[1] == 0xFF.toByte()) return Charsets.UTF_16BE
        if (n >= 2 && head[0] == 0xFF.toByte() && head[1] == 0xFE.toByte()) return Charsets.UTF_16LE
        val ascii = String(head, 0, n, Charsets.ISO_8859_1)
        val name = DECL.find(ascii)?.groupValues?.get(1) ?: return Charsets.UTF_8
        return runCatching { Charset.forName(name) }.getOrDefault(Charsets.UTF_8)
    }
}
