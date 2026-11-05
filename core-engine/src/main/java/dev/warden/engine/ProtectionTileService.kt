package dev.warden.engine

import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

/**
 * Quick Settings tile letting the user toggle Warden protection directly from the notification shade.
 */
class ProtectionTileService : TileService() {

    private var scope: CoroutineScope? = null
    private var listenJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        updateTileState(Engine.running.value)
        val s = CoroutineScope(Dispatchers.Main)
        scope = s
        listenJob = s.launch {
            Engine.running.collect { running ->
                updateTileState(running)
            }
        }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { toggleProtection() }
        } else {
            toggleProtection()
        }
    }

    private fun toggleProtection() {
        if (Engine.running.value) {
            WardenVpnService.stop(this)
            return
        }
        if (VpnService.prepare(this) == null) {
            WardenVpnService.start(this)
            return
        }
        startConsentActivity()
    }

    private fun startConsentActivity() {
        val appIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(this, 0, appIntent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(appIntent)
        }
    }

    private fun updateTileState(running: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_protection_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (running) R.string.tile_protection_on else R.string.tile_protection_off)
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_shield_check)
        tile.updateTile()
    }

    companion object {
        fun requestUpdate(context: Context) {
            runCatching {
                requestListeningState(context, ComponentName(context, ProtectionTileService::class.java))
            }
        }

        fun requestAddTile(context: Context, onResult: (Boolean, String?) -> Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val sbm = context.getSystemService(StatusBarManager::class.java)
                if (sbm == null) {
                    onResult(false, null)
                    return
                }
                val component = ComponentName(context, ProtectionTileService::class.java)
                val executor = Executor { command -> Handler(Looper.getMainLooper()).post(command) }
                sbm.requestAddTileService(
                    component,
                    context.getString(R.string.tile_protection_label),
                    Icon.createWithResource(context, R.drawable.ic_shield_check),
                    executor,
                ) { statusCode ->
                    when (statusCode) {
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> onResult(true, null)
                        StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> {
                            onResult(true, context.getString(R.string.tile_already_added))
                        }
                        else -> onResult(false, null)
                    }
                }
            } else {
                onResult(false, context.getString(R.string.tile_manual_add_instructions))
            }
        }
    }
}
