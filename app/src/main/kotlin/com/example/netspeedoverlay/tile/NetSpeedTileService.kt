package com.example.netspeedoverlay.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.netspeedoverlay.MainActivity
import com.example.netspeedoverlay.R
import com.example.netspeedoverlay.overlay.NetSpeedOverlayService

/**
 * Tile nelle Impostazioni rapide per accendere/spegnere il servizio senza
 * aprire l'app. Se manca il permesso overlay, apre l'app invece di provare
 * ad avviare comunque il servizio (fallirebbe silenziosamente).
 */
class NetSpeedTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (!NetSpeedOverlayService.canDrawOverlays(this)) {
            openApp()
            return
        }
        if (NetSpeedOverlayService.isRunning.value) {
            NetSpeedOverlayService.stop(this)
        } else {
            NetSpeedOverlayService.start(this)
        }
        updateTileState()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState() {
        val running = NetSpeedOverlayService.isRunning.value
        qsTile?.apply {
            state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.app_name)
            updateTile()
        }
    }
}
