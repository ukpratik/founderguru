// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ukpratik.founderguru.platform

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.ukpratik.founderguru.MainActivity
import io.github.ukpratik.founderguru.R
import io.github.ukpratik.founderguru.core.digest.DigestText
import io.github.ukpratik.founderguru.data.Tab
import io.github.ukpratik.founderguru.ui.theme.LightFg
import io.github.ukpratik.founderguru.work.DigestPoster
import androidx.compose.ui.graphics.toArgb

/**
 * The digest notification (US-012, design `notification.html`): one channel "Digest", default importance, no badge,
 * no actions, auto-cancel. The tap opens [MainActivity] on the tab whose divider shows the same n (LLD §4.9).
 */
class DigestNotifier(private val context: Context) : DigestPoster {

    override fun canPost(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** [canPost] is checked by the caller; the permission check is the same call. */
    @SuppressLint("MissingPermission")
    override fun post(text: DigestText, tab: Tab) {
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_TAB, tab.name)
            .putExtra(EXTRA_FROM_DIGEST, true)
        val pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(LightFg.accent.toArgb())
            .setContentTitle(text.title)
            .setContentText(text.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        if (canPost()) NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL = "digest"
        const val EXTRA_OPEN_TAB = "openTab"
        const val EXTRA_FROM_DIGEST = "fromDigest"
        private const val NOTIFICATION_ID = 1

        /** Created at start too, so Settings › Digest › "Open system settings" shows the channel. */
        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL) != null) return
            val channel = NotificationChannel(CHANNEL, context.getString(R.string.channel_digest), NotificationManager.IMPORTANCE_DEFAULT)
            channel.setShowBadge(false)
            manager.createNotificationChannel(channel)
        }
    }
}
