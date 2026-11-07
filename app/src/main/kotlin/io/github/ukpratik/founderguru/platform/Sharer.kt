// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.platform

import android.content.Context
import android.content.Intent

/**
 * Share text (US-009, D-017, D-023 G-8): `<title>\n<url>\n\nvia FounderGuru – <app link>`. Stored URLs already have
 * the tracking parameters removed at ingest (`UrlNormaliser.clean`); no redirect is resolved.
 */
object Sharer {
    const val APP_LINK = "https://github.com/ukpratik/founderguru"

    fun text(title: String, url: String): String = "$title\n$url\n\nvia FounderGuru – $APP_LINK"

    fun share(context: Context, title: String, url: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text(title, url))
        context.startActivity(Intent.createChooser(send, null))
    }
}
