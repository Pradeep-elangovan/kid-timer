package com.pradeep.kidtimer

import android.Manifest
import android.app.Activity
import android.app.StatusBarManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** One simple screen: pick the timer, grant the lock permission, add the tile. */
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var adminButton: Button
    private val radios = ArrayList<RadioButton>()

    // Held as a field: SharedPreferences keeps only a weak reference to listeners.
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onStart() {
        super.onStart()
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onResume() {
        super.onResume()
        render()
        // Make sure the timer is running if one is selected (e.g. after Android stopped it).
        if (Prefs.minutes(this) != 0 && Lock.isActive(this)) startTimerService()
    }

    override fun onStop() {
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onStop()
    }

    private fun buildUi() {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        column.addView(TextView(this).apply {
            text = "Kid Timer"
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
        })

        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, dp(8), 0, dp(20))
        }
        column.addView(status)

        column.addView(TextView(this).apply {
            text = "Lock the phone after this much screen time:"
            textSize = 16f
            setPadding(0, 0, 0, dp(8))
        })

        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        for (minutes in Prefs.OPTIONS) {
            val radio = RadioButton(this).apply {
                id = View.generateViewId()
                text = if (minutes == 0) "Off" else "$minutes minutes"
                textSize = 18f
                setPadding(dp(8), dp(10), dp(8), dp(10))
                setOnClickListener { choose(minutes) }
            }
            radios.add(radio)
            group.addView(radio)
        }
        column.addView(group)

        adminButton = Button(this).apply {
            text = "Allow phone lock (required)"
            setOnClickListener { requestAdmin() }
        }
        column.addView(adminButton)

        column.addView(Button(this).apply {
            text = "Add to Quick Settings"
            setOnClickListener { addTile() }
        })

        column.addView(Button(this).apply {
            text = "Battery: set to Unrestricted"
            setOnClickListener { openAppSettings() }
        })

        column.addView(TextView(this).apply {
            text = "The timer counts time the screen is on and the phone is unlocked. " +
                "A toast appears one minute before the phone locks. After a lock, " +
                "the count starts fresh at the next unlock. Your choice is remembered " +
                "until you change it."
            textSize = 14f
            setPadding(0, dp(20), 0, 0)
        })

        val scroll = ScrollView(this)
        scroll.addView(column)
        scroll.clipToPadding = false
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
    }

    private fun choose(minutes: Int) {
        if (minutes != 0 && !Lock.isActive(this)) {
            Toast.makeText(this, "Allow phone lock first", Toast.LENGTH_LONG).show()
            render()
            requestAdmin()
            return
        }
        if (minutes != Prefs.minutes(this)) Prefs.setMinutes(this, minutes)
        if (minutes != 0) startTimerService()
        render()
    }

    private fun render() {
        val minutes = Prefs.minutes(this)
        val index = Prefs.OPTIONS.indexOf(minutes).coerceAtLeast(0)
        radios[index].isChecked = true

        val admin = Lock.isActive(this)
        adminButton.visibility = if (admin) View.GONE else View.VISIBLE
        status.text = "Timer: ${Prefs.label(minutes)}\n" +
            "Phone lock permission: ${if (admin) "granted" else "missing"}"
    }

    private fun startTimerService() {
        try {
            startForegroundService(Intent(this, TimerService::class.java))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not start the timer service", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Lock.component(this))
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Needed so the timer can lock the screen when time is up."
            )
        startActivity(intent)
    }

    private fun addTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = getSystemService(StatusBarManager::class.java)
            manager.requestAddTileService(
                ComponentName(this, TimerTileService::class.java),
                getString(R.string.tile_label),
                Icon.createWithResource(this, R.drawable.ic_timer),
                mainExecutor
            ) { _ -> }
        } else {
            Toast.makeText(
                this,
                "Pull down Quick Settings, tap the pencil and drag in 'Screen timer'",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
