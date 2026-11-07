// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.time

/** Injected so every time-based decision is testable (LLD §1.1). Epoch milliseconds, UTC. */
fun interface Clock {
    fun now(): Long
}

object SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}
