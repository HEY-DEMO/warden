package dev.warden.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Manages the unclosable foreground protection notification.
 */
object ProtectionNotification {
    private const val CHANNEL = "warden_protection"
    const val NOTIFICATION_ID_RUNNING = 1

    @Volatile
    private var channelConfigured = false

    fun update(context: Context) {
        if (!Engine.running.value) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, nm)
        nm.notify(NOTIFICATION_ID_RUNNING, buildRunningNotification(context, Engine.blocked.get()))
    }

    fun cancelAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.cancel(NOTIFICATION_ID_RUNNING)
    }

    private fun ensureChannel(context: Context, nm: NotificationManager?) {
        if (nm == null || channelConfigured) return
        runCatching { nm.deleteNotificationChannel("status") }
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.engine_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
        channelConfigured = true
    }

    fun buildRunningNotification(context: Context, blocked: Int): Notification {
        val nm = context.getSystemService(NotificationManager::class.java)
        ensureChannel(context, nm)
        val stop = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ProtectionActionReceiver::class.java).setAction(ProtectionActionReceiver.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val text = if (blocked == 0) {
            context.getString(R.string.engine_notif_text)
        } else {
            context.resources.getQuantityString(R.plurals.engine_notif_blocked, blocked, blocked)
        }

        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_shield_check)
            .setContentTitle(context.getString(R.string.engine_notif_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.engine_notif_stop),
                    stop,
                ).build(),
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        return builder.build()
    }
}
