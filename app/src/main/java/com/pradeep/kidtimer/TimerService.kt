package com.pradeep.kidtimer

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast

/**
 * Counts screen-on time (screen on AND phone unlocked) and locks the phone
 * when the chosen limit is reached. Runs as a foreground service so Android
 * keeps it alive.
 */
class TimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var pm: PowerManager
    private lateinit var km: KeyguardManager

    private var used = 0L
    private var warned = false
    private var ticking = false
    private var lastTick = 0L
    private var lastPersist = 0L
    private var shownMinutes = -1

    // Held as fields: SharedPreferences keeps only weak references to listeners.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.KEY_MINUTES) onSettingChanged()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> startTicking()
                Intent.ACTION_SCREEN_OFF -> {
                    stopTicking()
                    persist()
                }
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            val delta = (now - lastTick).coerceIn(0L, 3000L)
            lastTick = now
            if (pm.isInteractive && !km.isKeyguardLocked) {
                used += delta
                check()
            }
            if (ticking) handler.postDelayed(this, 1000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        pm = getSystemService(PowerManager::class.java)
        km = getSystemService(KeyguardManager::class.java)
        createChannel()
        used = Prefs.usedMs(this)
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefListener)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must go foreground quickly after startForegroundService().
        showForeground(buildNotification())
        if (Prefs.minutes(this) == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (pm.isInteractive) startTicking()
        return START_STICKY
    }

    override fun onDestroy() {
        stopTicking()
        persist()
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: IllegalArgumentException) {
            // Already unregistered.
        }
        super.onDestroy()
    }

    private fun startTicking() {
        if (ticking || Prefs.minutes(this) == 0) return
        ticking = true
        lastTick = SystemClock.elapsedRealtime()
        handler.postDelayed(tick, 1000L)
    }

    private fun stopTicking() {
        ticking = false
        handler.removeCallbacks(tick)
    }

    private fun onSettingChanged() {
        used = 0L
        warned = false
        shownMinutes = -1
        if (Prefs.minutes(this) == 0) {
            stopTicking()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            notifyUpdate()
            if (pm.isInteractive) startTicking()
        }
    }

    private fun check() {
        val minutes = Prefs.minutes(this)
        if (minutes == 0) return
        val left = minutes * 60_000L - used
        if (left <= 0L) {
            fire()
            return
        }
        if (!warned && left <= 60_000L) {
            warned = true
            toast("1 minute of screen time left")
        }
        val leftMinutes = ((left + 59_999L) / 60_000L).toInt()
        if (leftMinutes != shownMinutes) {
            shownMinutes = leftMinutes
            notifyUpdate()
        }
        if (SystemClock.elapsedRealtime() - lastPersist >= 15_000L) persist()
    }

    private fun fire() {
        // Fresh count for the next unlock; the chosen timer itself stays as is.
        used = 0L
        warned = false
        shownMinutes = -1
        persist()
        if (Lock.isActive(this)) {
            Lock.lockNow(this)
        } else {
            toast("Time is up, but lock permission is missing. Open Kid Timer to allow it.")
        }
    }

    private fun persist() {
        Prefs.setUsedMs(this, used)
        lastPersist = SystemClock.elapsedRealtime()
    }

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    // --- Notification (Android requires one while a foreground service runs) ---

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Screen timer", NotificationManager.IMPORTANCE_LOW
        )
        channel.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val minutes = Prefs.minutes(this)
        val left = ((minutes * 60_000L - used + 59_999L) / 60_000L).coerceAtLeast(0L)
        val text = if (minutes == 0) "Screen timer is off" else "$left min left of $minutes min"
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("Screen timer")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    private fun notifyUpdate() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun showForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "timer"
        private const val NOTIF_ID = 1
    }
}
