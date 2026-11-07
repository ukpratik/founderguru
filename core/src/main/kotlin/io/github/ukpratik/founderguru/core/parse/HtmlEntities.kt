// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.parse

/** The HTML named entities feeds actually use (LLD §4.3). Shared by [CleaningReader] and [Html]. */
internal object HtmlEntities {
    val XML: Map<String, Int> = mapOf("amp" to '&'.code, "lt" to '<'.code, "gt" to '>'.code, "quot" to '"'.code, "apos" to '\''.code)

    val NAMED: Map<String, Int> = mapOf(
        "nbsp" to 160, "iexcl" to 161, "cent" to 162, "pound" to 163, "yen" to 165, "sect" to 167, "copy" to 169,
        "laquo" to 171, "shy" to 173, "reg" to 174, "deg" to 176, "plusmn" to 177, "para" to 182, "middot" to 183,
        "raquo" to 187, "frac14" to 188, "frac12" to 189, "frac34" to 190, "iquest" to 191, "times" to 215, "divide" to 247,
        "szlig" to 223, "agrave" to 224, "aacute" to 225, "acirc" to 226, "atilde" to 227, "auml" to 228, "ccedil" to 231,
        "egrave" to 232, "eacute" to 233, "ecirc" to 234, "iacute" to 237, "ntilde" to 241, "oacute" to 243, "ocirc" to 244,
        "otilde" to 245, "ouml" to 246, "uacute" to 250, "uuml" to 252, "Eacute" to 201,
        "ensp" to 8194, "emsp" to 8195, "thinsp" to 8201, "zwnj" to 8204, "zwj" to 8205, "ndash" to 8211, "mdash" to 8212,
        "lsquo" to 8216, "rsquo" to 8217, "sbquo" to 8218, "ldquo" to 8220, "rdquo" to 8221, "bdquo" to 8222,
        "dagger" to 8224, "bull" to 8226, "hellip" to 8230, "prime" to 8242, "euro" to 8364, "rupee" to 8377,
        "trade" to 8482, "larr" to 8592, "rarr" to 8594,
    )

    /** True for code points allowed in XML 1.0 (`#x9 | #xA | #xD | #x20-#xD7FF | #xE000-#xFFFD | #x10000-#x10FFFF`). */
    fun isXmlChar(cp: Int): Boolean =
        cp == 0x9 || cp == 0xA || cp == 0xD || cp in 0x20..0xD7FF || cp in 0xE000..0xFFFD || cp in 0x10000..0x10FFFF
}
