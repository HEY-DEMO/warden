package dev.warden.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

/**
 * Handles actions from notifications to toggle protection state without launching trampolines.
 */
class ProtectionActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_STOP -> {
                WardenVpnService.stop(context)
            }
            ACTION_START -> {
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent == null) {
                    WardenVpnService.start(context)
                } else {
                    val appIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    if (appIntent != null) {
                        appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(appIntent)
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_STOP = "dev.warden.engine.ACTION_STOP"
        const val ACTION_START = "dev.warden.engine.ACTION_START"
    }
}

