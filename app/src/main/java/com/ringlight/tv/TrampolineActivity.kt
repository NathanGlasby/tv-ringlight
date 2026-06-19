package com.ringlight.tv

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Zero-UI activity that forwards a shortcut intent to RingLightService and finishes instantly.
 * Theme.Transparent ensures nothing is drawn on screen.
 */
class TrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent?.action ?: RingLightService.ACTION_TOGGLE
        startForegroundService(Intent(this, RingLightService::class.java).apply { this.action = action })
        finish()
    }
}
