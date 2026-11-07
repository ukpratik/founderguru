// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import io.github.ukpratik.founderguru.ui.theme.DarkFg
import io.github.ukpratik.founderguru.ui.theme.LightFg
import androidx.compose.ui.graphics.toArgb

/** Opens a story (US-007, LLD §4.13): a Custom Tab, else the default browser, else [Result.NO_BROWSER]. */
object ArticleOpener {
    enum class Result { OPENED, NO_BROWSER, NOT_WEB }

    fun isWebUrl(url: String): Boolean = url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

    fun open(context: Context, url: String, dark: Boolean): Result {
        if (!isWebUrl(url)) return Result.NOT_WEB
        val uri = url.toUri()
        return try {
            if (CustomTabsClient.getPackageName(context, null) != null) {
                customTab(dark).launchUrl(context, uri)
            } else {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
            }
            Result.OPENED
        } catch (_: ActivityNotFoundException) {
            Result.NO_BROWSER
        }
    }

    /** Open in browser (overflow menu): skips the Custom Tab. */
    fun openInBrowser(context: Context, url: String): Result {
        if (!isWebUrl(url)) return Result.NOT_WEB
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
            Result.OPENED
        } catch (_: ActivityNotFoundException) {
            Result.NO_BROWSER
        }
    }

    private fun customTab(dark: Boolean): CustomTabsIntent = CustomTabsIntent.Builder()
        .setColorScheme(if (dark) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
        .setColorSchemeParams(
            CustomTabsIntent.COLOR_SCHEME_LIGHT,
            CustomTabColorSchemeParams.Builder().setToolbarColor(LightFg.bg.toArgb()).setNavigationBarColor(LightFg.bg.toArgb()).build(),
        )
        .setColorSchemeParams(
            CustomTabsIntent.COLOR_SCHEME_DARK,
            CustomTabColorSchemeParams.Builder().setToolbarColor(DarkFg.bg.toArgb()).setNavigationBarColor(DarkFg.bg.toArgb()).build(),
        )
        .setShareState(CustomTabsIntent.SHARE_STATE_ON)
        .build()
}
