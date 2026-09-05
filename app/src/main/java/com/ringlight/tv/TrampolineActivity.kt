package com.ringlight.tv

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast

/** A real resumed activity lets a remote command start the foreground overlay on recent Android. */
class TrampolineActivity : Activity() {
    private var dispatched = false

    override fun onPostResume() {
        super.onPostResume()
        if (dispatched) return
        dispatched = true
        val command = ShortcutActions.find(intent.action)
        if (command == null) {
            finish()
            return
        }
        val prefs = Prefs(this)
        prefs.recordShortcut(command.action, System.currentTimeMillis())
        val needsOverlay = command.action == RingLightService.ACTION_TOGGLE && !prefs.isOn
        if (needsOverlay && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            Toast.makeText(this, R.string.permission_shortcut_help, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        try {
            // Cycling an inactive light only changes the saved setting. Nothing needs to listen in the background.
            when {
                command.action == RingLightService.ACTION_CYCLE_COLOR && !prefs.isOn ->
                    prefs.colorIndex = (prefs.colorIndex + 1) % RingLightSettings.COLORS.size
                command.action == RingLightService.ACTION_CYCLE_INTENSITY && !prefs.isOn ->
                    prefs.intensityIndex = (prefs.intensityIndex + 1) % RingLightSettings.INTENSITY_LEVELS.size
                command.action == RingLightService.ACTION_TURN_OFF && !prefs.isOn -> Unit
                else -> startForegroundService(Intent(this, RingLightService::class.java).setAction(command.action))
            }
        } catch (error: RuntimeException) {
            Log.w("RingLightShortcut", "Cannot run remote command", error)
            Toast.makeText(this, R.string.shortcut_start_failed, Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
