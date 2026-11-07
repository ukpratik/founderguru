// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.ui.components

import androidx.annotation.StringRes
import io.github.ukpratik.founderguru.R
import io.github.ukpratik.founderguru.core.feed.LensFilter
import io.github.ukpratik.founderguru.core.model.Lens
import io.github.ukpratik.founderguru.core.model.Region

/** Display names (design copy). Order of the onboarding list = [Labels.ONBOARDING_ORDER]. */
object Labels {
    val ONBOARDING_ORDER = listOf(Region.IN, Region.EU, Region.NA, Region.AF, Region.SEA, Region.LATAM, Region.GLOBAL)
    val LIMITED = setOf(Region.SEA, Region.LATAM)

    @StringRes fun region(r: Region): Int = when (r) {
        Region.IN -> R.string.region_in
        Region.EU -> R.string.region_eu
        Region.NA -> R.string.region_na
        Region.AF -> R.string.region_af
        Region.SEA -> R.string.region_sea
        Region.LATAM -> R.string.region_latam
        Region.GLOBAL -> R.string.region_global
    }

    @StringRes fun lens(l: Lens): Int = when (l) {
        Lens.FUNDING -> R.string.lens_funding
        Lens.LAUNCHES -> R.string.lens_launches
        Lens.TRENDS -> R.string.lens_trends
        Lens.PLAYBOOKS -> R.string.lens_playbooks
    }

    @StringRes fun lens(f: LensFilter): Int = when (f) {
        LensFilter.All -> R.string.lens_all
        is LensFilter.Only -> lens(f.lens)
    }

    /** "No playbook stories in India" (design-system §7). */
    @StringRes fun lensNoun(l: Lens): Int = when (l) {
        Lens.FUNDING -> R.string.lens_noun_funding
        Lens.LAUNCHES -> R.string.lens_noun_launches
        Lens.TRENDS -> R.string.lens_noun_trends
        Lens.PLAYBOOKS -> R.string.lens_noun_playbooks
    }

    /** The chip row, in design order. */
    val CHIPS: List<LensFilter> = listOf(LensFilter.All) + Lens.entries.map(LensFilter::Only)
}
