package com.pradeep.kidtimer

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * The Quick Settings tile. Each tap cycles Off -> 15 -> 30 -> 45 -> 60 -> Off.
 * When the phone is locked, Android asks for the unlock first, so a kid
 * cannot change it from the lock screen.
 */
class TimerTileService : TileService() {

    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        if (isLocked) {
            unlockAndRun { cycle() }
        } else {
            cycle()
        }
    }

    private fun cycle() {
        val next = Prefs.next(this)
        if (next != 0 && !Lock.isActive(this)) {
            // Can't lock without permission; send the user to the app to grant it.
            openApp()
            return
        }
        Prefs.setMinutes(this, next)
        if (next != 0) {
            try {
                startForegroundService(Intent(this, TimerService::class.java))
            } catch (e: Exception) {
                // Android refused a background start; the app starts it on open.
                openApp()
            }
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val minutes = Prefs.minutes(this)
        tile.label = getString(R.string.tile_label)
        if (minutes == 0) {
            tile.subtitle = "Off"
            tile.state = Tile.STATE_INACTIVE
        } else {
            val left = ((minutes * 60_000L - Prefs.usedMs(this) + 59_999L) / 60_000L)
                .coerceIn(0L, minutes.toLong())
            tile.subtitle = "$minutes min · ${left}m left"
            tile.state = Tile.STATE_ACTIVE
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
