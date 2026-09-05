package com.ringlight.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/** Restore only a light that was active and still has time left. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = Prefs(context)
        if (!prefs.isOn) return
        val remaining = AutoOffTimer(context, prefs).remainingMillis()
        if (!Settings.canDrawOverlays(context) || remaining != null && remaining <= 0L) {
            prefs.isOn = false
            prefs.clearAutoOffDeadline()
            return
        }
        try {
            context.startForegroundService(Intent(context, RingLightService::class.java))
        } catch (error: RuntimeException) {
            // Keep the request so opening RingLight can restore it if firmware blocks boot startup.
            Log.w("RingLightBoot", "TV refused background restoration; open RingLight to resume", error)
        }
    }
}
