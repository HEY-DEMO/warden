package dev.warden.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles actions from the ongoing protection notification.
 */
class ProtectionActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_STOP) {
            WardenVpnService.stop(context)
        }
    }

    companion object {
        const val ACTION_STOP = "dev.warden.engine.ACTION_STOP"
    }
}
