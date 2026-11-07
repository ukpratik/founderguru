// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.core.region

import com.google.common.truth.Truth.assertThat
import io.github.ukpratik.founderguru.core.model.Region
import org.junit.Test

class RegionHintTest {
    @Test fun `country codes map to regions`() {
        assertThat(RegionHint.suggest("in")).isEqualTo(Region.IN)
        assertThat(RegionHint.suggest("DE")).isEqualTo(Region.EU)
        assertThat(RegionHint.suggest("gb")).isEqualTo(Region.EU)
        assertThat(RegionHint.suggest("CH")).isEqualTo(Region.EU)
        assertThat(RegionHint.suggest("NO")).isEqualTo(Region.EU)
        assertThat(RegionHint.suggest("US")).isEqualTo(Region.NA)
        assertThat(RegionHint.suggest("CA")).isEqualTo(Region.NA)
        assertThat(RegionHint.suggest("NG")).isEqualTo(Region.AF)
        assertThat(RegionHint.suggest("EG")).isEqualTo(Region.AF)
        assertThat(RegionHint.suggest("SG")).isEqualTo(Region.SEA)
        assertThat(RegionHint.suggest("BR")).isEqualTo(Region.LATAM)
        assertThat(RegionHint.suggest("MX")).isEqualTo(Region.LATAM)
    }

    @Test fun `no match suggests Global only`() {
        assertThat(RegionHint.suggest("AE")).isEqualTo(Region.GLOBAL)
        assertThat(RegionHint.suggest("JP")).isEqualTo(Region.GLOBAL)
        assertThat(RegionHint.suggest(null, "", null)).isEqualTo(Region.GLOBAL)
        assertThat(RegionHint.suggest()).isEqualTo(Region.GLOBAL)
    }

    @Test fun `network first, then SIM, then locale`() {
        assertThat(RegionHint.suggest("", "KE", "IN")).isEqualTo(Region.AF)
        assertThat(RegionHint.suggest(null, null, "IN")).isEqualTo(Region.IN)
        assertThat(RegionHint.suggest("FR", "IN", "US")).isEqualTo(Region.EU)
    }

    @Test fun `the lists have the documented sizes`() {
        assertThat(RegionHint.AFRICA).hasSize(54)
        assertThat(RegionHint.EUROPE).hasSize(32)
        assertThat(RegionHint.SOUTHEAST_ASIA).hasSize(11)
    }
}
