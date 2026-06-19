package com.ringlight.tv

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class RingLightService : Service() {

    companion object {
        const val ACTION_TOGGLE = "com.ringlight.tv.TOGGLE"
        const val ACTION_CYCLE_COLOR = "com.ringlight.tv.CYCLE_COLOR"
        const val ACTION_CYCLE_INTENSITY = "com.ringlight.tv.CYCLE_INTENSITY"
        const val ACTION_REFRESH = "com.ringlight.tv.REFRESH"

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "ringlight"

        val COLORS = intArrayOf(
            Color.WHITE,
            0xFFFFD27F.toInt(), // Warm White
            0xFFFFFF4D.toInt(), // Yellow
            0xFFFF4D4D.toInt(), // Red
            0xFF4DFF91.toInt(), // Green
            0xFF4DB8FF.toInt(), // Blue
            0xFFB84DFF.toInt(), // Purple
            0xFFFF4DC8.toInt(), // Pink
        )

        val COLOR_NAMES = arrayOf(
            "White", "Warm White", "Yellow", "Red", "Green", "Blue", "Purple", "Pink"
        )

        val INTENSITY_LEVELS = floatArrayOf(0.25f, 0.50f, 0.75f, 1.0f)
        val INTENSITY_LABELS = arrayOf("25%", "50%", "75%", "100%")
    }

    private lateinit var wm: WindowManager
    private var ringView: RingLightView? = null
    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    private var autoOffRunnable: Runnable? = null

    // Burn-in protection: shift overlay by a few pixels on a slow cycle
    private val burnInRunnable = object : Runnable {
        private val offsets = arrayOf(0 to 0, 2 to 0, 2 to 2, 0 to 2, -2 to 2, -2 to 0, -2 to -2, 0 to -2)
        private var step = 0
        override fun run() {
            val view = ringView ?: return
            val lp = view.layoutParams as? WindowManager.LayoutParams ?: return
            val (dx, dy) = offsets[step % offsets.size]
            lp.x = dx
            lp.y = dy
            try { wm.updateViewLayout(view, lp) } catch (_: Exception) {}
            step++
            handler.postDelayed(this, 30_000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(false))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> toggle()
            ACTION_CYCLE_COLOR -> cycleColor()
            ACTION_CYCLE_INTENSITY -> cycleIntensity()
            ACTION_REFRESH -> refreshRing()
            null -> if (prefs.isOn) showRing() // restore after boot or crash-restart
        }
        return START_STICKY
    }

    private fun toggle() {
        if (ringView != null) hideRing() else showRing()
    }

    private fun showRing() {
        if (ringView != null) return

        val view = RingLightView(this).apply {
            ringColor = COLORS[prefs.colorIndex]
            intensity = INTENSITY_LEVELS[prefs.intensityIndex]
            thickness = prefs.thickness
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )

        try {
            wm.addView(view, lp)
        } catch (e: Exception) {
            return // overlay permission was revoked
        }
        ringView = view
        prefs.isOn = true
        scheduleAutoOff()
        handler.postDelayed(burnInRunnable, 30_000L)
        updateNotification(true)
    }

    private fun hideRing() {
        ringView?.let { wm.removeView(it) }
        ringView = null
        prefs.isOn = false
        cancelAutoOff()
        handler.removeCallbacks(burnInRunnable)
        updateNotification(false)
    }

    private fun cycleColor() {
        val next = (prefs.colorIndex + 1) % COLORS.size
        prefs.colorIndex = next
        ringView?.apply {
            ringColor = COLORS[next]
            invalidate()
        }
    }

    private fun cycleIntensity() {
        val next = (prefs.intensityIndex + 1) % INTENSITY_LEVELS.size
        prefs.intensityIndex = next
        ringView?.apply {
            intensity = INTENSITY_LEVELS[next]
            invalidate()
        }
    }

    private fun refreshRing() {
        ringView?.apply {
            ringColor = COLORS[prefs.colorIndex]
            intensity = INTENSITY_LEVELS[prefs.intensityIndex]
            thickness = prefs.thickness
            invalidate()
        }
    }

    private fun scheduleAutoOff() {
        cancelAutoOff()
        if (!prefs.autoOffEnabled) return
        val delayMs = prefs.autoOffMinutes * 60_000L
        autoOffRunnable = Runnable { hideRing() }.also { handler.postDelayed(it, delayMs) }
    }

    private fun cancelAutoOff() {
        autoOffRunnable?.let { handler.removeCallbacks(it) }
        autoOffRunnable = null
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "RingLight", NotificationManager.IMPORTANCE_LOW)
        ch.description = "RingLight status"
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(ringOn: Boolean): Notification {
        val settingsIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val toggleIntent = PendingIntent.getService(
            this, 1, Intent(this, RingLightService::class.java).apply { action = ACTION_TOGGLE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ring)
            .setContentTitle(if (ringOn) "RingLight — On" else "RingLight — Ready")
            .setContentText(if (ringOn) "Tap to open settings" else "Listening for Button Mapper")
            .setContentIntent(settingsIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .apply { if (ringOn) addAction(0, "Turn Off", toggleIntent) }
            .build()
    }

    private fun updateNotification(ringOn: Boolean) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(ringOn))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideRing()
        super.onDestroy()
    }
}
