package com.pradeep.kidtimer

import android.content.Context
import android.content.SharedPreferences

/** Saved settings. The chosen timer (0 = Off) stays exactly as left until changed. */
object Prefs {
    const val KEY_MINUTES = "minutes"
    const val KEY_USED = "used_ms"

    /** The cycle order used by the tile. 0 means Off. */
    val OPTIONS = intArrayOf(0, 15, 30, 45, 60)

    fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("kid_timer", Context.MODE_PRIVATE)

    fun minutes(c: Context): Int = sp(c).getInt(KEY_MINUTES, 0)

    /** Changing the setting also restarts the used-time count. */
    fun setMinutes(c: Context, minutes: Int) {
        sp(c).edit().putInt(KEY_MINUTES, minutes).putLong(KEY_USED, 0L).apply()
    }

    fun usedMs(c: Context): Long = sp(c).getLong(KEY_USED, 0L)

    fun setUsedMs(c: Context, value: Long) {
        sp(c).edit().putLong(KEY_USED, value).apply()
    }

    fun next(c: Context): Int {
        val i = OPTIONS.indexOf(minutes(c)).coerceAtLeast(0)
        return OPTIONS[(i + 1) % OPTIONS.size]
    }

    fun label(minutes: Int): String = if (minutes == 0) "Off" else "$minutes min"
}
