// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.tag

import io.github.ukpratik.founderguru.core.model.Region

/**
 * Curated place names per region (US-002, D-010, LLD §4.8), matched whole-word and case-sensitively, applied only to
 * stories from Global sources. Ambiguous names (Georgia, Jordan, Chad, Guinea, Niger, Turkey, Nice, Santiago, Victoria)
 * are deliberately absent. "New Mexico" / "New England" don't count, and "US" inside an all-caps headline run doesn't
 * either ("BUILT FOR US BY").
 */
object Gazetteer {
    private val terms: Map<Region, List<String>> = mapOf(
        Region.IN to listOf(
            "India", "Indian", "Indians", "Bengaluru", "Bangalore", "Mumbai", "Delhi", "New Delhi", "Hyderabad", "Chennai", "Pune",
            "Gurugram", "Gurgaon", "Noida", "Kolkata", "Ahmedabad",
        ),
        Region.EU to listOf(
            "Europe", "European", "Europeans", "EU", "UK", "Britain", "British", "(?<!New )England", "Scotland", "Ireland", "Germany",
            "German", "France", "French", "Spain", "Portugal", "Italy", "Netherlands", "Dutch", "Belgium", "Switzerland", "Swiss",
            "Austria", "Sweden", "Swedish", "Norway", "Denmark", "Finland", "Estonia", "Poland", "Czech", "Lithuania", "Latvia",
            "Greece", "Romania", "Ukraine", "London", "Berlin", "Paris", "Munich", "Amsterdam", "Stockholm", "Madrid",
            "Barcelona", "Lisbon", "Dublin", "Helsinki", "Copenhagen", "Zurich", "Tallinn", "Warsaw", "Milan", "Vienna",
        ),
        Region.NA to listOf(
            "United States", "U\\.S\\.", "U\\.S", "(?<![A-Z]{2,12} )US(?! [A-Z]{2,})", "American", "Americans", "Canada", "Canadian",
            "Silicon Valley", "San Francisco", "New York", "NYC", "Seattle", "Boston", "Austin", "Los Angeles", "Toronto",
            "Vancouver", "Montreal", "Montréal", "Waterloo",
        ),
        Region.AF to listOf(
            "Africa", "African", "Nigeria", "Nigerian", "Kenya", "Kenyan", "Ghana", "Ghanaian", "South Africa", "Egypt", "Egyptian",
            "Morocco", "Moroccan", "Ethiopia", "Rwanda", "Senegal", "Tanzania", "Uganda", "Ivory Coast", "Côte d['’]Ivoire",
            "Tunisia", "Zambia", "Cameroon", "Lagos", "Nairobi", "Cairo", "Johannesburg", "Cape Town", "Accra", "Kigali",
        ),
        Region.SEA to listOf(
            "Southeast Asia", "South-East Asia", "Singapore", "Indonesia", "Indonesian", "Malaysia", "Vietnam", "Thailand",
            "Philippines", "Filipino", "Jakarta", "Bangkok", "Manila", "Kuala Lumpur", "Ho Chi Minh City", "Hanoi",
        ),
        Region.LATAM to listOf(
            "Latin America", "Latin American", "LatAm", "Brazil", "Brazilian", "(?<!New )Mexico", "Mexican", "Argentina",
            "Colombia", "Colombian", "Chile", "Chilean", "Peru", "Uruguay", "São Paulo", "Sao Paulo", "Mexico City",
            "Buenos Aires", "Bogotá", "Bogota",
        ),
    )

    private val matchers: Map<Region, WordMatcher> = terms.mapValues { (_, t) -> WordMatcher(emptyList(), t) }

    /** Bitmask of [Region.mentionBit]s mentioned in [text]. */
    fun mentions(text: String): Int = matchers.entries.fold(0) { acc, (region, m) -> if (m.matches(text)) acc or region.mentionBit else acc }
}
