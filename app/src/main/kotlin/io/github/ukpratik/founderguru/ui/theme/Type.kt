// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.ukpratik.founderguru.R

/** Bundled OFL fonts (design-system §3, NFR-013): Latin subsets in `res/font/`, built by `scripts/subset-fonts.sh`. */
val Newsreader = FontFamily(
    Font(R.font.newsreader_medium, FontWeight.Medium),
    Font(R.font.newsreader_semibold, FontWeight.SemiBold),
    Font(R.font.newsreader_medium_italic, FontWeight.Medium, FontStyle.Italic),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun style(family: FontFamily, weight: FontWeight, size: Int, line: Int, tracking: TextUnit = 0.sp, italic: Boolean = false) =
    TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )

/** The type scale of design-system §3. */
val FgTypography = Typography(
    displayMedium = style(Newsreader, FontWeight.Medium, 44, 48, (-0.02).em),
    displaySmall = style(Newsreader, FontWeight.Medium, 36, 40, (-0.015).em),
    headlineLarge = style(Newsreader, FontWeight.Medium, 34, 38, (-0.01).em),
    headlineMedium = style(Newsreader, FontWeight.Medium, 32, 36, (-0.01).em),
    headlineSmall = style(Newsreader, FontWeight.Medium, 24, 30),
    titleLarge = style(Newsreader, FontWeight.SemiBold, 19, 25, (-0.003).em),
    titleMedium = style(Newsreader, FontWeight.Medium, 17, 23),
    titleSmall = style(Inter, FontWeight.SemiBold, 15, 20),
    bodyLarge = style(Inter, FontWeight.Normal, 16, 24),
    bodyMedium = style(Inter, FontWeight.Normal, 14, 20),
    bodySmall = style(Inter, FontWeight.Normal, 13, 18),
    labelLarge = style(Inter, FontWeight.SemiBold, 15, 20),
    labelMedium = style(Inter, FontWeight.SemiBold, 12, 16),
    labelSmall = style(Inter, FontWeight.Bold, 11, 14, 0.12.em),
)

/** Styles outside the M3 roles (design-system §3 "Extras"). */
object FgType {
    val dateline = style(Newsreader, FontWeight.Medium, 14, 18, italic = true)
    val hotRank = style(Newsreader, FontWeight.Medium, 13, 18, italic = true)
    val resultCount = style(Newsreader, FontWeight.Medium, 30, 30)
    val chip = style(Inter, FontWeight.Medium, 14, 20)
    val tag = style(Inter, FontWeight.SemiBold, 11, 14, 0.06.em)
    val wordmark = style(Newsreader, FontWeight.Medium, 20, 24, (-0.01).em, italic = true)
}
