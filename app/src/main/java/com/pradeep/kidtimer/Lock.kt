package com.pradeep.kidtimer

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context

/** Thin wrapper around Device Admin, which is what lets the app lock the screen. */
object Lock {
    fun component(c: Context): ComponentName = ComponentName(c, AdminReceiver::class.java)

    private fun dpm(c: Context): DevicePolicyManager =
        c.getSystemService(DevicePolicyManager::class.java)

    fun isActive(c: Context): Boolean = dpm(c).isAdminActive(component(c))

    fun lockNow(c: Context) {
        if (isActive(c)) dpm(c).lockNow()
    }
}
