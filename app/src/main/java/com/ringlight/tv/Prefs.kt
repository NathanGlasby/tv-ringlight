package com.ringlight.tv

import android.content.Context

class Prefs(context: Context) {
    private val p = context.getSharedPreferences("ringlight", Context.MODE_PRIVATE)

    var isOn: Boolean
        get() = p.getBoolean("is_on", false)
        set(v) = p.edit().putBoolean("is_on", v).apply()

    var colorIndex: Int
        get() = p.getInt("color_index", 0)
        set(v) = p.edit().putInt("color_index", v).apply()

    var intensityIndex: Int
        get() = p.getInt("intensity_index", 3) // default 100%
        set(v) = p.edit().putInt("intensity_index", v).apply()

    // stored as tenths of a percent (e.g. 15 = 15%)
    var thicknessPct: Int
        get() = p.getInt("thickness_pct", 15)
        set(v) = p.edit().putInt("thickness_pct", v).apply()

    val thickness: Float get() = thicknessPct / 100f

    var autoOffEnabled: Boolean
        get() = p.getBoolean("auto_off_enabled", true)
        set(v) = p.edit().putBoolean("auto_off_enabled", v).apply()

    var autoOffMinutes: Int
        get() = p.getInt("auto_off_minutes", 180) // default 3 hours
        set(v) = p.edit().putInt("auto_off_minutes", v).apply()
}
