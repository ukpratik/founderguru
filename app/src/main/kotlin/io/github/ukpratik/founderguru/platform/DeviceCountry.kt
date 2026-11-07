// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.platform

import android.content.Context
import android.telephony.TelephonyManager
import io.github.ukpratik.founderguru.core.model.Region
import io.github.ukpratik.founderguru.core.region.RegionHint
import java.util.Locale

/** The onboarding suggestion (US-001, LLD §4.12): network country, else SIM country, else locale. No permission needed. */
object DeviceCountry {
    fun suggestedRegion(context: Context): Region {
        val tm = context.getSystemService(TelephonyManager::class.java)
        return RegionHint.suggest(tm?.networkCountryIso, tm?.simCountryIso, Locale.getDefault().country)
    }
}
