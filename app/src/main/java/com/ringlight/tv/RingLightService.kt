package com.ringlight.tv

import android.app.AppOpsManager
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
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/** Owns the overlay and runs in the foreground only while the light is on. */
class RingLightService : Service() {
    companion object {
        // Keep these action names stable for existing Button Mapper assignments.
        const val ACTION_TOGGLE = "com.ringlight.tv.TOGGLE"
        const val ACTION_TURN_OFF = "com.ringlight.tv.TURN_OFF"
        const val ACTION_CYCLE_COLOR = "com.ringlight.tv.CYCLE_COLOR"
        const val ACTION_CYCLE_INTENSITY = "com.ringlight.tv.CYCLE_INTENSITY"
        const val ACTION_REFRESH = "com.ringlight.tv.REFRESH"
        const val ACTION_TIMER_CHANGED = "com.ringlight.tv.TIMER_CHANGED"

        val COLORS: IntArray get() = RingLightSettings.COLORS.toIntArray()
        val COLOR_NAMES: Array<String> get() = RingLightSettings.COLOR_NAMES.toTypedArray()
        val INTENSITY_LEVELS: FloatArray get() = RingLightSettings.INTENSITY_LEVELS.toFloatArray()
        val INTENSITY_LABELS: Array<String> get() = RingLightSettings.INTENSITY_LABELS.toTypedArray()

        private const val TAG = "RingLightService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "ringlight"
        private const val SHIFT_INTERVAL_MILLIS = 30_000L
        private const val TIMER_CHECK_INTERVAL_MILLIS = 60_000L
        private val SHIFT_OFFSETS = arrayOf(
            0 to 0, 2 to 0, 2 to 2, 0 to 2, -2 to 2, -2 to 0, -2 to -2, 0 to -2,
        )
    }

    private lateinit var windowManager: WindowManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var prefs: Prefs
    private lateinit var timer: AutoOffTimer
    private var ringView: RingLightView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var shiftStep = 0
    private var destroyed = false
    private var watchingPermission = false

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (!destroyed && ringView != null) {
            when (key) {
                Prefs.COLOR_INDEX, Prefs.INTENSITY_INDEX, Prefs.THICKNESS_PCT -> refreshAppearance()
                Prefs.AUTO_OFF_ENABLED, Prefs.AUTO_OFF_MINUTES -> scheduleAutoOff(reset = true)
                Prefs.IS_ON -> if (!prefs.isOn) turnOff()
                null -> {
                    if (!prefs.isOn) turnOff() else {
                        refreshAppearance()
                        scheduleAutoOff(reset = true)
                    }
                }
            }
        }
    }

    private val permissionListener = AppOpsManager.OnOpChangedListener { op, changedPackage ->
        if (op == AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW && changedPackage == packageName) {
            handler.post { if (!destroyed && !Settings.canDrawOverlays(this)) turnOff() }
        }
    }

    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ringView == null) return
            if (!Settings.canDrawOverlays(this@RingLightService)) turnOff()
            else scheduleAutoOff(reset = false)
        }
    }

    private val autoOffRunnable = Runnable { checkAutoOff() }

    private val burnInRunnable = object : Runnable {
        override fun run() {
            val view = ringView ?: return
            if (!Settings.canDrawOverlays(this@RingLightService)) {
                turnOff()
                return
            }
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return
            val (dx, dy) = SHIFT_OFFSETS[shiftStep++ % SHIFT_OFFSETS.size]
            params.x = dx
            params.y = dy
            try {
                windowManager.updateViewLayout(view, params)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Overlay window is no longer available", error)
                turnOff()
                return
            }
            handler.postDelayed(this, SHIFT_INTERVAL_MILLIS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        timer = AutoOffTimer(this, prefs)
        windowManager = getSystemService(WindowManager::class.java)
        notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW)
                .apply { description = getString(R.string.notification_channel_description) },
        )
        prefs.registerListener(preferenceListener)
        // These are protected system broadcasts. Android 14 requires no export flag for this case.
        registerReceiver(
            clockReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_SCREEN_ON)
            },
        )
        try {
            getSystemService(AppOpsManager::class.java).startWatchingMode(
                AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, permissionListener,
            )
            watchingPermission = true
        } catch (error: RuntimeException) {
            // Some TV firmware does not expose AppOps watching. Periodic checks still apply.
            Log.w(TAG, "Cannot watch overlay permission changes", error)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> if (prefs.isOn) turnOff() else showRing(resetTimer = true)
            ACTION_TURN_OFF -> turnOff()
            ACTION_CYCLE_COLOR -> {
                prefs.colorIndex = (prefs.colorIndex + 1) % RingLightSettings.COLORS.size
                restoreIfOn()
            }
            ACTION_CYCLE_INTENSITY -> {
                prefs.intensityIndex = (prefs.intensityIndex + 1) % RingLightSettings.INTENSITY_LEVELS.size
                restoreIfOn()
            }
            ACTION_TIMER_CHANGED -> if (prefs.isOn) showRing(resetTimer = true) else stopWhenIdle()
            ACTION_REFRESH, null -> restoreIfOn()
            else -> stopWhenIdle()
        }
        return if (ringView != null) START_STICKY else START_NOT_STICKY
    }

    private fun restoreIfOn() {
        if (prefs.isOn) showRing(resetTimer = false) else stopWhenIdle()
    }

    private fun showRing(resetTimer: Boolean) {
        if (!Settings.canDrawOverlays(this)) {
            turnOff()
            return
        }
        val remaining = timer.synchronize(resetTimer)
        if (remaining != null && remaining <= 0L) {
            turnOff()
            return
        }
        if (ringView == null) {
            val view = RingLightView(this)
            applyAppearance(view)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT,
            ).apply { title = getString(R.string.app_name) }
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit
                override fun onViewDetachedFromWindow(view: View) {
                    if (!destroyed && ringView === view) turnOff()
                }
            })
            try {
                windowManager.addView(view, params)
                ringView = view
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    buildNotification(),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    } else 0,
                )
            } catch (error: RuntimeException) {
                Log.w(TAG, "Cannot display the ring light", error)
                turnOff()
                return
            }
            prefs.isOn = true
            shiftStep = 0
            handler.postDelayed(burnInRunnable, SHIFT_INTERVAL_MILLIS)
        } else {
            refreshAppearance()
        }
        armAutoOff(remaining)
    }

    private fun applyAppearance(view: RingLightView) {
        view.ringColor = RingLightSettings.COLORS[prefs.colorIndex]
        view.intensity = RingLightSettings.INTENSITY_LEVELS[prefs.intensityIndex]
        view.thickness = prefs.thickness
    }

    private fun refreshAppearance() {
        if (!Settings.canDrawOverlays(this)) {
            turnOff()
            return
        }
        ringView?.let {
            applyAppearance(it)
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun scheduleAutoOff(reset: Boolean) {
        armAutoOff(timer.synchronize(reset))
    }

    private fun checkAutoOff() {
        if (ringView != null) armAutoOff(timer.remainingMillis())
    }

    private fun armAutoOff(remaining: Long?) {
        handler.removeCallbacks(autoOffRunnable)
        if (ringView == null || remaining == null) return
        if (remaining <= 0L) turnOff()
        else handler.postDelayed(autoOffRunnable, remaining.coerceAtMost(TIMER_CHECK_INTERVAL_MILLIS))
    }

    private fun turnOff() {
        // Clear the view first so preference and detach listeners cannot re-enter cleanup.
        detachRing()
        prefs.isOn = false
        prefs.clearAutoOffDeadline()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopWhenIdle() {
        if (ringView == null) {
            prefs.clearAutoOffDeadline()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun detachRing() {
        handler.removeCallbacks(autoOffRunnable)
        handler.removeCallbacks(burnInRunnable)
        val view = ringView
        ringView = null
        if (view != null) {
            try {
                windowManager.removeViewImmediate(view)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Overlay window was already removed", error)
            }
        }
    }

    private fun buildNotification(): Notification {
        val settingsIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val offIntent = PendingIntent.getService(
            this, 1, Intent(this, RingLightService::class.java).setAction(ACTION_TURN_OFF),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ring)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(
                R.string.notification_appearance,
                RingLightSettings.COLOR_NAMES[prefs.colorIndex],
                RingLightSettings.INTENSITY_LABELS[prefs.intensityIndex],
            ))
            .setContentIntent(settingsIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.notification_turn_off), offIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroyed = true
        prefs.unregisterListener(preferenceListener)
        unregisterReceiver(clockReceiver)
        if (watchingPermission) {
            getSystemService(AppOpsManager::class.java).stopWatchingMode(permissionListener)
        }
        detachRing()
        handler.removeCallbacksAndMessages(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // Android may recreate a sticky service. Preserve the requested state and deadline.
        super.onDestroy()
    }
}
