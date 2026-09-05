package com.ringlight.tv

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/** A duration uses the monotonic clock on this boot and a wall-clock fallback after reboot. */
internal class AutoOffTimer(
    context: Context,
    private val prefs: Prefs,
    private val wallTime: () -> Long = System::currentTimeMillis,
    private val elapsedTime: () -> Long = SystemClock::elapsedRealtime,
    private val bootCount: () -> Int = {
        try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        } catch (_: SecurityException) {
            -1
        }
    },
) {
    fun remainingMillis(): Long? = remainingAt(wallTime(), elapsedTime(), bootCount())

    private fun remainingAt(wall: Long, elapsed: Long, currentBoot: Int): Long? {
        if (!prefs.autoOffEnabled) return null
        val duration = prefs.autoOffMinutes * 60_000L
        if (prefs.autoOffDeadlineMillis == 0L) return duration
        val remaining = if (
            currentBoot >= 0 && currentBoot == prefs.autoOffBootCount &&
            prefs.autoOffElapsedDeadlineMillis > 0L
        ) {
            prefs.autoOffElapsedDeadlineMillis - elapsed
        } else {
            prefs.autoOffDeadlineMillis - wall
        }
        return remaining.coerceAtMost(duration)
    }

    /** Capture each clock once so repeated synchronization cannot slowly extend the timer. */
    fun synchronize(reset: Boolean): Long? {
        if (!prefs.autoOffEnabled) {
            prefs.clearAutoOffDeadline()
            return null
        }
        val wall = wallTime()
        val elapsed = elapsedTime()
        val boot = bootCount()
        val remaining = if (reset) prefs.autoOffMinutes * 60_000L else remainingAt(wall, elapsed, boot)!!
        if (remaining > 0L) {
            prefs.setAutoOffDeadline(wall + remaining, elapsed + remaining, boot)
        }
        return remaining
    }
}
