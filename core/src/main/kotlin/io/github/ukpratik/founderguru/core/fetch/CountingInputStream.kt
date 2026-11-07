// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.fetch

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/** Thrown when a feed body passes the byte cap (LLD §4.2: 3 MB). */
class BodyTooLargeException(limit: Long) : IOException("body larger than $limit bytes")

/** Counts bytes read and throws [BodyTooLargeException] once more than [limit] bytes have been read. */
class CountingInputStream(input: InputStream, private val limit: Long) : FilterInputStream(input) {
    var count: Long = 0
        private set

    private var mark: Long = 0

    override fun read(): Int {
        val b = super.read()
        if (b >= 0) add(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) add(n.toLong())
        return n
    }

    override fun skip(n: Long): Long {
        val skipped = super.skip(n)
        if (skipped > 0) add(skipped)
        return skipped
    }

    override fun mark(readlimit: Int) {
        super.mark(readlimit)
        mark = count
    }

    override fun reset() {
        super.reset()
        count = mark
    }

    private fun add(n: Long) {
        count += n
        if (count > limit) throw BodyTooLargeException(limit)
    }
}
