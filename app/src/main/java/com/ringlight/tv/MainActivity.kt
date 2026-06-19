package com.ringlight.tv

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs

    private lateinit var permissionBanner: LinearLayout
    private lateinit var toggleBtn: Button
    private lateinit var thicknessValue: TextView
    private lateinit var intensityValue: TextView
    private lateinit var autoOffSwitch: Switch
    private lateinit var durationRow: LinearLayout
    private lateinit var durationValue: TextView
    private lateinit var colorRow: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_main)

        permissionBanner = findViewById(R.id.permission_banner)
        toggleBtn = findViewById(R.id.btn_toggle)
        thicknessValue = findViewById(R.id.thickness_value)
        intensityValue = findViewById(R.id.intensity_value)
        autoOffSwitch = findViewById(R.id.auto_off_switch)
        durationRow = findViewById(R.id.duration_row)
        durationValue = findViewById(R.id.duration_value)
        colorRow = findViewById(R.id.color_row)

        setupPermissionBanner()
        setupToggleButton()
        setupThickness()
        setupColors()
        setupIntensity()
        setupAutoOff()
        registerDynamicShortcuts()
    }

    private fun registerDynamicShortcuts() {
        try {
            val sm = getSystemService(ShortcutManager::class.java) ?: return
            val shortcuts = listOf(
                shortcut("toggle_ring",    "Toggle",    "Toggle RingLight",          R.drawable.ic_ring,      RingLightService.ACTION_TOGGLE),
                shortcut("cycle_color",    "Colour",    "Cycle RingLight Colour",    R.drawable.ic_color,     RingLightService.ACTION_CYCLE_COLOR),
                shortcut("cycle_intensity","Intensity", "Cycle RingLight Intensity", R.drawable.ic_intensity, RingLightService.ACTION_CYCLE_INTENSITY)
            )
            sm.setDynamicShortcuts(shortcuts)
        } catch (_: Exception) {
            // ShortcutManager not supported on this device/ROM — shortcuts must be configured manually
        }
    }

    private fun shortcut(id: String, short: String, long: String, icon: Int, action: String): ShortcutInfo =
        ShortcutInfo.Builder(this, id)
            .setShortLabel(short)
            .setLongLabel(long)
            .setIcon(Icon.createWithResource(this, icon))
            .setIntent(Intent(action).setClass(this, TrampolineActivity::class.java))
            .build()

    override fun onResume() {
        super.onResume()
        setupPermissionBanner()
        updateToggleLabel()
    }

    // ── Permission ──────────────────────────────────────────────────────────

    private fun setupPermissionBanner() {
        val granted = Settings.canDrawOverlays(this)
        permissionBanner.visibility = if (granted) View.GONE else View.VISIBLE
        if (!granted) {
            findViewById<Button>(R.id.btn_grant_permission).setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        } else {
            ensureServiceRunning()
        }
    }

    private fun ensureServiceRunning() {
        startForegroundService(Intent(this, RingLightService::class.java))
    }

    // ── Toggle ───────────────────────────────────────────────────────────────

    private fun setupToggleButton() {
        updateToggleLabel()
        toggleBtn.setOnClickListener {
            sendServiceAction(RingLightService.ACTION_TOGGLE)
            // Briefly delay the label update so prefs are written by the service first
            toggleBtn.postDelayed({ updateToggleLabel() }, 200)
        }
    }

    private fun updateToggleLabel() {
        toggleBtn.text = if (prefs.isOn) "Turn Ring Off" else "Turn Ring On"
    }

    // ── Thickness ────────────────────────────────────────────────────────────

    private fun setupThickness() {
        updateThicknessLabel()
        findViewById<Button>(R.id.btn_thickness_minus).setOnClickListener {
            prefs.thicknessPct = (prefs.thicknessPct - 5).coerceAtLeast(5)
            updateThicknessLabel()
            sendServiceAction(RingLightService.ACTION_REFRESH)
        }
        findViewById<Button>(R.id.btn_thickness_plus).setOnClickListener {
            prefs.thicknessPct = (prefs.thicknessPct + 5).coerceAtMost(40)
            updateThicknessLabel()
            sendServiceAction(RingLightService.ACTION_REFRESH)
        }
    }

    private fun updateThicknessLabel() {
        thicknessValue.text = "${prefs.thicknessPct}%"
    }

    // ── Colors ───────────────────────────────────────────────────────────────

    private fun setupColors() {
        colorRow.removeAllViews()
        RingLightService.COLORS.forEachIndexed { index, color ->
            val btn = Button(this).apply {
                text = ""
                contentDescription = RingLightService.COLOR_NAMES[index]
                isFocusable = true
                isFocusableInTouchMode = true

                val size = dpToPx(64)
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = dpToPx(12)
                }

                background = colorSwatchDrawable(color, index == prefs.colorIndex)

                setOnClickListener {
                    prefs.colorIndex = index
                    refreshColorSwatches()
                    sendServiceAction(RingLightService.ACTION_REFRESH)
                }
                setOnFocusChangeListener { _, hasFocus ->
                    background = colorSwatchDrawable(color, hasFocus || index == prefs.colorIndex)
                }
            }
            colorRow.addView(btn)
        }
    }

    private fun refreshColorSwatches() {
        RingLightService.COLORS.forEachIndexed { index, color ->
            (colorRow.getChildAt(index) as? Button)?.background =
                colorSwatchDrawable(color, index == prefs.colorIndex)
        }
    }

    private fun colorSwatchDrawable(color: Int, selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            if (selected) {
                setStroke(dpToPx(4), Color.WHITE)
            } else {
                setStroke(dpToPx(2), Color.argb(120, 255, 255, 255))
            }
        }
    }

    // ── Intensity ────────────────────────────────────────────────────────────

    private fun setupIntensity() {
        updateIntensityLabel()
        findViewById<Button>(R.id.btn_intensity_minus).setOnClickListener {
            prefs.intensityIndex = (prefs.intensityIndex - 1).coerceAtLeast(0)
            updateIntensityLabel()
            sendServiceAction(RingLightService.ACTION_REFRESH)
        }
        findViewById<Button>(R.id.btn_intensity_plus).setOnClickListener {
            prefs.intensityIndex = (prefs.intensityIndex + 1).coerceAtMost(RingLightService.INTENSITY_LEVELS.size - 1)
            updateIntensityLabel()
            sendServiceAction(RingLightService.ACTION_REFRESH)
        }
    }

    private fun updateIntensityLabel() {
        intensityValue.text = RingLightService.INTENSITY_LABELS[prefs.intensityIndex]
    }

    // ── Auto-off ─────────────────────────────────────────────────────────────

    private fun setupAutoOff() {
        autoOffSwitch.isChecked = prefs.autoOffEnabled
        updateDurationRow()
        updateDurationLabel()

        autoOffSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.autoOffEnabled = checked
            updateDurationRow()
        }

        findViewById<Button>(R.id.btn_duration_minus).setOnClickListener {
            prefs.autoOffMinutes = decrementDuration(prefs.autoOffMinutes)
            updateDurationLabel()
        }
        findViewById<Button>(R.id.btn_duration_plus).setOnClickListener {
            prefs.autoOffMinutes = incrementDuration(prefs.autoOffMinutes)
            updateDurationLabel()
        }
    }

    private fun updateDurationRow() {
        durationRow.visibility = if (prefs.autoOffEnabled) View.VISIBLE else View.GONE
    }

    private fun updateDurationLabel() {
        val mins = prefs.autoOffMinutes
        durationValue.text = when {
            mins < 60 -> "${mins}m"
            mins % 60 == 0 -> "${mins / 60}h"
            else -> "${mins / 60}h ${mins % 60}m"
        }
    }

    // Steps: 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 360, 480
    private val durationSteps = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 360, 480)

    private fun decrementDuration(current: Int): Int {
        val idx = durationSteps.indexOfLast { it < current }.coerceAtLeast(0)
        return durationSteps[idx]
    }

    private fun incrementDuration(current: Int): Int {
        val idx = durationSteps.indexOfFirst { it > current }
        return if (idx == -1) durationSteps.last() else durationSteps[idx]
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun sendServiceAction(action: String) {
        startForegroundService(Intent(this, RingLightService::class.java).apply { this.action = action })
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()
}
