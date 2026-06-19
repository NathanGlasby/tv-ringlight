package com.ringlight.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Start the service — it will restore the ring if it was on before reboot
        context.startForegroundService(Intent(context, RingLightService::class.java))
    }
}
