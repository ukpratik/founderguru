// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.region

import io.github.ukpratik.founderguru.core.model.Region

/**
 * Suggested onboarding region from a country code (US-001, LLD §4.12). The app passes the network country, else the SIM
 * country, else the locale country (no permission needed). No match → [Region.GLOBAL] ("Global only" is suggested).
 */
object RegionHint {
    /** EU and EEA members, plus the UK and Switzerland. */
    val EUROPE = setOf(
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT",
        "NL", "PL", "PT", "RO", "SK", "SI", "ES", "SE", "IS", "LI", "NO", "GB", "CH",
    )

    /** The 54 African states. */
    val AFRICA = setOf(
        "DZ", "AO", "BJ", "BW", "BF", "BI", "CV", "CM", "CF", "TD", "KM", "CD", "CG", "CI", "DJ", "EG", "GQ", "ER", "SZ",
        "ET", "GA", "GM", "GH", "GN", "GW", "KE", "LS", "LR", "LY", "MG", "MW", "ML", "MR", "MU", "MA", "MZ", "NA", "NE",
        "NG", "RW", "ST", "SN", "SC", "SL", "SO", "ZA", "SS", "SD", "TZ", "TG", "TN", "UG", "ZM", "ZW",
    )

    val SOUTHEAST_ASIA = setOf("SG", "ID", "MY", "VN", "TH", "PH", "KH", "LA", "MM", "BN", "TL")

    /** Mexico, Central America and South America. */
    val LATIN_AMERICA = setOf(
        "MX", "BZ", "CR", "SV", "GT", "HN", "NI", "PA",
        "AR", "BO", "BR", "CL", "CO", "EC", "GY", "PY", "PE", "SR", "UY", "VE",
    )

    /** The first non-blank code wins (network, SIM, locale). Codes are ISO 3166-1 alpha-2, any case. */
    fun suggest(vararg countryCodes: String?): Region {
        val code = countryCodes.firstOrNull { !it.isNullOrBlank() }?.trim()?.uppercase() ?: return Region.GLOBAL
        return when (code) {
            "IN" -> Region.IN
            "US", "CA" -> Region.NA
            "UK" -> Region.EU
            in EUROPE -> Region.EU
            in AFRICA -> Region.AF
            in SOUTHEAST_ASIA -> Region.SEA
            in LATIN_AMERICA -> Region.LATAM
            else -> Region.GLOBAL
        }
    }
}
