package com.pradeep.kidtimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the timer after a reboot or an app update, if a timer is selected. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.minutes(context) != 0) {
            try {
                context.startForegroundService(Intent(context, TimerService::class.java))
            } catch (e: Exception) {
                // Not allowed to start right now; the app or tile will start it later.
            }
        }
    }
}
