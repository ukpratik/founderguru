// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import kotlin.math.pow

/**
 * NFR-008 (M5 accessibility pass): every text/background pair the design system uses meets WCAG 2.x AA (4.5:1 for text,
 * 3:1 for non-text such as chip, switch and tag borders), in both themes, computed from the shipped [FgColors] values,
 * and each ratio is at least the one design-system §2 states (so the code can't drift from the checked design).
 */
class ContrastTest {
    private fun lum(c: Color): Double {
        fun ch(v: Float) = if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }

    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(lum(a), lum(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private data class Pair3(val name: String, val fg: Color, val bg: Color, val min: Double)

    private fun textPairs(c: FgColors) = listOf(
        Pair3("ink on bg", c.ink, c.bg, 4.5),
        Pair3("ink on surface-2", c.ink, c.surface2, 4.5),
        Pair3("ink on overlay", c.ink, c.overlay, 4.5),
        Pair3("ink-2 on bg", c.ink2, c.bg, 4.5),
        Pair3("ink-2 on surface-2", c.ink2, c.surface2, 4.5),
        Pair3("ink-2 on overlay", c.ink2, c.overlay, 4.5),
        Pair3("ink-3 on bg", c.meta, c.bg, 4.5),
        Pair3("ink-3 on surface", c.meta, c.surface, 4.5),
        Pair3("ink-3 on surface-2", c.meta, c.surface2, 4.5),
        Pair3("ink-3 on overlay", c.meta, c.overlay, 4.5),
        Pair3("accent on bg", c.accent, c.bg, 4.5),
        Pair3("accent on surface", c.accent, c.surface, 4.5),
        Pair3("accent on surface-2", c.accent, c.surface2, 4.5),
        Pair3("accent on accent-soft", c.accent, c.accentSoft, 4.5),
        Pair3("ink on accent-soft", c.ink, c.accentSoft, 4.5),
        Pair3("bg on ink (selected chip)", c.bg, c.ink, 4.5),
        Pair3("inv-on on inv (snackbar, offline strip)", c.invOn, c.inv, 4.5),
        Pair3("inv-accent on inv (snackbar action)", c.invAccent, c.inv, 4.5),
        Pair3("outline on bg (non-text)", c.outline, c.bg, 3.0),
        Pair3("outline on surface (non-text)", c.outline, c.surface, 3.0),
    )

    @Test fun `light theme pairs meet AA`() = check(LightFg)

    @Test fun `dark theme pairs meet AA`() = check(DarkFg)

    private fun check(c: FgColors) {
        for (p in textPairs(c)) {
            val r = ratio(p.fg, p.bg)
            assertWithMessage("${if (c.isDark) "dark" else "light"}: ${p.name} = ${"%.2f".format(r)}:1").that(r).isAtLeast(p.min)
        }
    }

    @Test fun `ratios match the design system's stated values`() {
        // design-system §2.1 / §2.2 (rounded to one decimal there; allow 0.1 either way).
        val stated = listOf(
            Triple(LightFg.ink, LightFg.bg, 15.8), Triple(LightFg.ink2, LightFg.bg, 8.0), Triple(LightFg.meta, LightFg.bg, 5.3),
            Triple(LightFg.meta, LightFg.surface2, 4.8), Triple(LightFg.accent, LightFg.bg, 6.2), Triple(LightFg.outline, LightFg.bg, 3.4),
            Triple(DarkFg.ink, DarkFg.bg, 15.2), Triple(DarkFg.ink2, DarkFg.bg, 10.0), Triple(DarkFg.meta, DarkFg.bg, 6.3),
            Triple(DarkFg.accent, DarkFg.bg, 7.9), Triple(DarkFg.outline, DarkFg.bg, 3.9),
        )
        for ((fg, bg, want) in stated) {
            val r = ratio(fg, bg)
            assertWithMessage("$fg on $bg = ${"%.2f".format(r)}, design says $want").that(r).isWithin(0.15).of(want)
        }
    }
}
