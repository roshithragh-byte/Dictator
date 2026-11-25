package com.fieldflow.overlay

import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

/**
 * S4-3: Android Quick Settings Tile to toggle the FieldFlow floating bubble on/off.
 */
class FieldFlowTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = if (Settings.canDrawOverlays(this@FieldFlowTileService)) {
                Tile.STATE_INACTIVE
            } else {
                Tile.STATE_UNAVAILABLE
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        if (!Settings.canDrawOverlays(this)) return

        val isActive = tile.state == Tile.STATE_ACTIVE
        val intent = Intent(this, OverlayCaptureService::class.java).apply {
            action = if (isActive) {
                OverlayCaptureService.ACTION_HIDE_OVERLAY
            } else {
                OverlayCaptureService.ACTION_SHOW_OVERLAY
            }
        }

        if (isActive) {
            startService(intent)
            tile.state = Tile.STATE_INACTIVE
        } else {
            ContextCompat.startForegroundService(this, intent)
            tile.state = Tile.STATE_ACTIVE
        }
        tile.updateTile()
    }
}
