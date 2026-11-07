// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.parse

import java.io.Reader

/**
 * Streaming XML repair (LLD §4.3, US-004), O(1) memory:
 * 1. drops a leading BOM and anything before the first `<`;
 * 2. drops characters that aren't valid XML 1.0 (and unpaired surrogates);
 * 3. outside CDATA, comments and processing instructions, rewrites `&`: numeric and the 5 XML references pass (invalid
 *    targets become U+FFFD), known HTML named entities become numeric references, anything else becomes `&amp;`.
 */
class CleaningReader(private val source: Reader) : Reader() {
    private enum class State { PROLOG, TEXT, TAG, CDATA, COMMENT, PI }

    private var state = State.PROLOG
    private var quote: Char? = null // inside a quoted attribute value in TAG

    // Push-back stack for look-ahead (LLD: 16 chars; entity look-ahead needs at most 13).
    private val pushback = CharArray(PUSHBACK)
    private var pushed = 0

    // Raw input buffer.
    private val inBuf = CharArray(4096)
    private var inPos = 0
    private var inLen = 0

    // Pending output.
    private val out = StringBuilder(64)
    private var outPos = 0
    private var eof = false

    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        var n = 0
        while (n < len) {
            if (outPos == out.length) {
                out.setLength(0)
                outPos = 0
                if (eof || !step()) {
                    eof = true
                    break
                }
                continue
            }
            cbuf[off + n++] = out[outPos++]
        }
        return if (n == 0 && eof) -1 else n
    }

    override fun close() = source.close()

    private fun next(): Int {
        if (pushed > 0) return pushback[--pushed].code
        if (inPos == inLen) {
            inLen = source.read(inBuf, 0, inBuf.size)
            inPos = 0
            if (inLen <= 0) {
                inLen = 0
                return -1
            }
        }
        return inBuf[inPos++].code
    }

    private fun unread(c: Char) {
        check(pushed < PUSHBACK) { "push-back overflow" }
        pushback[pushed++] = c
    }

    private fun unread(s: CharSequence) {
        for (i in s.length - 1 downTo 0) unread(s[i])
    }

    /** Reads [s.length] chars; true (consumed) if they equal [s], otherwise pushes them back. */
    private fun lookingAt(s: String): Boolean {
        val got = StringBuilder(s.length)
        for (ch in s) {
            val c = next()
            if (c < 0) break
            got.append(c.toChar())
            if (c.toChar() != ch) break
        }
        if (got.toString() == s) return true
        unread(got)
        return false
    }

    /** Processes one input unit into [out]. Returns false at end of input. */
    private fun step(): Boolean {
        val c = next()
        if (c < 0) return false
        val ch = c.toChar()
        // Surrogates: a valid pair passes, anything unpaired is dropped.
        if (Character.isHighSurrogate(ch)) {
            val d = next()
            if (d >= 0 && Character.isLowSurrogate(d.toChar())) {
                if (state != State.PROLOG) out.append(ch).append(d.toChar())
            } else if (d >= 0) {
                unread(d.toChar())
            }
            return true
        }
        if (!HtmlEntities.isXmlChar(c)) return true
        when (state) {
            State.PROLOG -> if (ch == '<') {
                state = State.TEXT
                unread(ch)
            }
            State.TEXT -> when (ch) {
                '<' -> {
                    out.append('<')
                    state = when {
                        lookingAt("!--") -> State.COMMENT.also { out.append("!--") }
                        lookingAt("![CDATA[") -> State.CDATA.also { out.append("![CDATA[") }
                        lookingAt("?") -> State.PI.also { out.append('?') }
                        else -> State.TAG
                    }
                }
                '&' -> entity()
                else -> out.append(ch)
            }
            State.TAG -> when {
                ch == '&' && quote != null -> entity()
                ch == '"' || ch == '\'' -> {
                    quote = when (quote) {
                        null -> ch
                        ch -> null
                        else -> quote
                    }
                    out.append(ch)
                }
                ch == '>' && quote == null -> {
                    out.append(ch)
                    state = State.TEXT
                }
                else -> out.append(ch)
            }
            State.CDATA -> {
                out.append(ch)
                if (ch == ']' && lookingAt("]>")) {
                    out.append("]>")
                    state = State.TEXT
                }
            }
            State.COMMENT -> {
                out.append(ch)
                if (ch == '-' && lookingAt("->")) {
                    out.append("->")
                    state = State.TEXT
                }
            }
            State.PI -> {
                out.append(ch)
                if (ch == '?' && lookingAt(">")) {
                    out.append('>')
                    state = State.TEXT
                }
            }
        }
        return true
    }

    /** Called after a `&` in text or an attribute value. */
    private fun entity() {
        val look = StringBuilder(MAX_ENTITY)
        var terminated = false
        while (look.length < MAX_ENTITY) {
            val c = next()
            if (c < 0) break
            val ch = c.toChar()
            if (ch == ';') {
                terminated = true
                break
            }
            look.append(ch)
            if (!(ch.isLetterOrDigit() || ch == '#') || ch.code > 127) break
        }
        if (terminated) {
            val name = look.toString()
            numericTarget(name)?.let { cp ->
                // Re-emitted in canonical decimal form (XML only allows a lowercase `x` in hex references).
                if (cp >= 0 && HtmlEntities.isXmlChar(cp)) out.append("&#").append(cp).append(';') else out.append(REPLACEMENT)
                return
            }
            if (name in HtmlEntities.XML) {
                out.append('&').append(name).append(';')
                return
            }
            HtmlEntities.NAMED[name]?.let {
                out.append("&#").append(it).append(';')
                return
            }
            // Unknown name: escape the ampersand and re-read the rest as ordinary text.
            out.append("&amp;")
            unread(';')
            unread(look)
            return
        }
        out.append("&amp;")
        unread(look)
    }

    /** `#123` / `#x1F` → the code point (-1 if out of range); anything else → null. */
    private fun numericTarget(name: String): Int? {
        if (name.length < 2 || name[0] != '#') return null
        val hex = name[1] == 'x' || name[1] == 'X'
        val digits = if (hex) name.substring(2) else name.substring(1)
        if (digits.isEmpty() || digits.length > 8) return null
        if (!digits.all { if (hex) it.isDigit() || it.lowercaseChar() in 'a'..'f' else it.isDigit() }) return null
        val v = digits.toLongOrNull(if (hex) 16 else 10) ?: return null
        return if (v > 0x10FFFF) -1 else v.toInt()
    }

    private companion object {
        const val PUSHBACK = 32
        const val MAX_ENTITY = 12
        const val REPLACEMENT = '�'
    }
}
