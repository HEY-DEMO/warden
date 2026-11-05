package dev.warden.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Manages active protection notifications and optional controls to pause or resume protection.
 */
object ProtectionNotification {
    private const val CHANNEL = "warden_protection"
    const val NOTIFICATION_ID_RUNNING = 1
    const val NOTIFICATION_ID_OFF = 2
    private const val PREFS_NAME = "warden"
    private const val KEY_NOTIF_CONTROLS = "notification_controls"

    fun isControlEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIF_CONTROLS, false)
    }

    fun setControlEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIF_CONTROLS, enabled)
            .apply()
        update(context)
    }

    fun update(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(context, nm)
        if (Engine.running.value) {
            cancelOffNotification(context)
            nm.notify(NOTIFICATION_ID_RUNNING, buildRunningNotification(context, Engine.blocked.get()))
        } else if (isControlEnabled(context)) {
            nm.notify(NOTIFICATION_ID_OFF, buildOffNotification(context))
        } else {
            cancelAll(context)
        }
    }

    fun cancelOffNotification(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.cancel(NOTIFICATION_ID_OFF)
    }

    fun cancelAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.cancel(NOTIFICATION_ID_RUNNING)
        nm.cancel(NOTIFICATION_ID_OFF)
    }

    private fun ensureChannel(context: Context, nm: NotificationManager?) {
        if (nm == null) return
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

    private fun buildOffNotification(context: Context): Notification {
        val start = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, ProtectionActionReceiver::class.java).setAction(ProtectionActionReceiver.ACTION_START),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val text = context.getString(R.string.engine_notif_off_text)

        return Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_shield_check)
            .setContentTitle(context.getString(R.string.engine_notif_off_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(true)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.engine_notif_turn_on),
                    start,
                ).build(),
            )
            .build()
    }
}
