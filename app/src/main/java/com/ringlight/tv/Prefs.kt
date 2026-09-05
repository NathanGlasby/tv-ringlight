package com.ringlight.tv

import android.content.Context
import android.content.SharedPreferences

/** Keeps the original preference file and keys so installed copies retain their settings. */
class Prefs(context: Context) {
    private val storage = context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var isOn: Boolean
        get() = boolean(IS_ON, false)
        set(value) { storage.edit().putBoolean(IS_ON, value).apply() }

    var colorIndex: Int
        get() = RingLightSettings.colorIndex(integer(COLOR_INDEX, RingLightSettings.DEFAULT_COLOR_INDEX))
        set(value) { storage.edit().putInt(COLOR_INDEX, RingLightSettings.colorIndex(value)).apply() }

    var intensityIndex: Int
        get() = RingLightSettings.intensityIndex(integer(INTENSITY_INDEX, RingLightSettings.DEFAULT_INTENSITY_INDEX))
        set(value) { storage.edit().putInt(INTENSITY_INDEX, RingLightSettings.intensityIndex(value)).apply() }

    /** Percentage of the screen's shorter side, stored as a whole number. */
    var thicknessPct: Int
        get() = RingLightSettings.thicknessPct(integer(THICKNESS_PCT, RingLightSettings.DEFAULT_THICKNESS_PCT))
        set(value) { storage.edit().putInt(THICKNESS_PCT, RingLightSettings.thicknessPct(value)).apply() }

    val thickness: Float get() = thicknessPct / 100f

    var autoOffEnabled: Boolean
        get() = boolean(AUTO_OFF_ENABLED, RingLightSettings.DEFAULT_AUTO_OFF_ENABLED)
        set(value) { storage.edit().putBoolean(AUTO_OFF_ENABLED, value).apply() }

    var autoOffMinutes: Int
        get() = RingLightSettings.autoOffMinutes(integer(AUTO_OFF_MINUTES, RingLightSettings.DEFAULT_AUTO_OFF_MINUTES))
        set(value) { storage.edit().putInt(AUTO_OFF_MINUTES, RingLightSettings.autoOffMinutes(value)).apply() }

    /** Wall time supports reboot recovery and the settings screen's countdown. Zero means no timer. */
    var autoOffDeadlineMillis: Long
        get() = long(AUTO_OFF_DEADLINE_MILLIS, 0L).coerceAtLeast(0L)
        set(value) { storage.edit().putLong(AUTO_OFF_DEADLINE_MILLIS, value.coerceAtLeast(0L)).apply() }

    /** Monotonic time keeps manual clock changes from extending the current session. */
    var autoOffElapsedDeadlineMillis: Long
        get() = long(AUTO_OFF_ELAPSED_DEADLINE_MILLIS, 0L).coerceAtLeast(0L)
        set(value) { storage.edit().putLong(AUTO_OFF_ELAPSED_DEADLINE_MILLIS, value.coerceAtLeast(0L)).apply() }

    var autoOffBootCount: Int
        get() = integer(AUTO_OFF_BOOT_COUNT, -1).coerceAtLeast(-1)
        set(value) { storage.edit().putInt(AUTO_OFF_BOOT_COUNT, value.coerceAtLeast(-1)).apply() }

    fun setAutoOffDeadline(wallMillis: Long, elapsedMillis: Long, bootCount: Int) {
        storage.edit()
            .putLong(AUTO_OFF_DEADLINE_MILLIS, wallMillis.coerceAtLeast(0L))
            .putLong(AUTO_OFF_ELAPSED_DEADLINE_MILLIS, elapsedMillis.coerceAtLeast(0L))
            .putInt(AUTO_OFF_BOOT_COUNT, bootCount.coerceAtLeast(-1))
            .apply()
    }

    fun clearAutoOffDeadline() = setAutoOffDeadline(0L, 0L, -1)

    val lastShortcutAction: String?
        get() = try { storage.getString(LAST_SHORTCUT_ACTION, null) } catch (_: ClassCastException) { null }

    val lastShortcutAtMillis: Long
        get() = long(LAST_SHORTCUT_AT_MILLIS, 0L).coerceAtLeast(0L)

    fun recordShortcut(action: String, atMillis: Long) {
        storage.edit()
            .putString(LAST_SHORTCUT_ACTION, action)
            .putLong(LAST_SHORTCUT_AT_MILLIS, atMillis.coerceAtLeast(0L))
            .apply()
    }

    /** Reset the controls without switching the light on or off. */
    fun resetSettings() {
        storage.edit()
            .putInt(COLOR_INDEX, RingLightSettings.DEFAULT_COLOR_INDEX)
            .putInt(INTENSITY_INDEX, RingLightSettings.DEFAULT_INTENSITY_INDEX)
            .putInt(THICKNESS_PCT, RingLightSettings.DEFAULT_THICKNESS_PCT)
            .putBoolean(AUTO_OFF_ENABLED, RingLightSettings.DEFAULT_AUTO_OFF_ENABLED)
            .putInt(AUTO_OFF_MINUTES, RingLightSettings.DEFAULT_AUTO_OFF_MINUTES)
            .apply()
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        storage.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        storage.unregisterOnSharedPreferenceChangeListener(listener)

    // Old backups or edits made through debugging tools may contain a different type.
    private fun integer(key: String, default: Int): Int =
        try { storage.getInt(key, default) } catch (_: ClassCastException) { default }

    private fun long(key: String, default: Long): Long =
        try { storage.getLong(key, default) } catch (_: ClassCastException) { default }

    private fun boolean(key: String, default: Boolean): Boolean =
        try { storage.getBoolean(key, default) } catch (_: ClassCastException) { default }

    companion object {
        const val FILE_NAME = "ringlight"
        const val IS_ON = "is_on"
        const val COLOR_INDEX = "color_index"
        const val INTENSITY_INDEX = "intensity_index"
        const val THICKNESS_PCT = "thickness_pct"
        const val AUTO_OFF_ENABLED = "auto_off_enabled"
        const val AUTO_OFF_MINUTES = "auto_off_minutes"
        const val AUTO_OFF_DEADLINE_MILLIS = "auto_off_deadline_millis"
        const val AUTO_OFF_ELAPSED_DEADLINE_MILLIS = "auto_off_elapsed_deadline_millis"
        const val AUTO_OFF_BOOT_COUNT = "auto_off_boot_count"
        const val LAST_SHORTCUT_ACTION = "last_shortcut_action"
        const val LAST_SHORTCUT_AT_MILLIS = "last_shortcut_at_millis"
    }
}
