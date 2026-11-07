package com.pradeep.kidtimer

import android.app.admin.DeviceAdminReceiver

/** Needs no logic; its presence (with the force-lock policy) lets the app call lockNow(). */
class AdminReceiver : DeviceAdminReceiver()
