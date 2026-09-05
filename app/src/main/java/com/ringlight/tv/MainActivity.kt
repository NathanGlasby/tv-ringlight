package com.ringlight.tv

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateUtils
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

/** TV settings and a preview of the same view used by the overlay. */
class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var preview: RingLightView
    private lateinit var toggleButton: Button
    private lateinit var permissionBanner: View
    private lateinit var autoOffSwitch: SwitchCompat
    private lateinit var colorRow: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var rendering = false
    private var requestedPowerState: Boolean? = null

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread {
            if (requestedPowerState == prefs.isOn) clearPowerRequest()
            render()
        }
    }

    private val powerRequestTimeout = Runnable {
        val requested = requestedPowerState ?: return@Runnable
        clearPowerRequest()
        render()
        if (prefs.isOn != requested) showMessage(R.string.light_start_failed)
    }

    private val countdownTick = object : Runnable {
        override fun run() {
            renderTimer()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_main)
        preview = findViewById(R.id.light_preview)
        findViewById<View>(R.id.preview_frame).clipToOutline = true
        toggleButton = findViewById(R.id.btn_toggle)
        permissionBanner = findViewById(R.id.permission_banner)
        autoOffSwitch = findViewById(R.id.auto_off_switch)
        colorRow = findViewById(R.id.color_row)

        findViewById<Button>(R.id.btn_grant_permission).setOnClickListener { openOverlaySettings() }
        toggleButton.setOnClickListener { toggleLight() }
        setupColors()
        setupSteppers()
        autoOffSwitch.setOnCheckedChangeListener { _, enabled ->
            if (!rendering) prefs.autoOffEnabled = enabled
        }
        findViewById<Button>(R.id.btn_shortcuts).setOnClickListener { showRemoteSetup() }
        findViewById<Button>(R.id.btn_reset).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.reset_title)
                .setMessage(R.string.reset_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.reset_confirm) { _, _ ->
                    prefs.resetSettings()
                    render()
                    showMessage(R.string.settings_reset)
                }
                .show()
        }
        toggleButton.nextFocusRightId = colorRow.getChildAt(0).id
        render()
        if (!Settings.canDrawOverlays(this)) findViewById<View>(R.id.btn_grant_permission).requestFocus()
        else toggleButton.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        prefs.registerListener(preferenceListener)
        handler.post(countdownTick)
    }

    override fun onResume() {
        super.onResume()
        // Opening settings must never start a light that the user has switched off.
        if (prefs.isOn) {
            sendServiceAction(
                if (Settings.canDrawOverlays(this)) RingLightService.ACTION_REFRESH
                else RingLightService.ACTION_TURN_OFF
            )
        }
        render()
    }

    override fun onStop() {
        prefs.unregisterListener(preferenceListener)
        handler.removeCallbacks(countdownTick)
        clearPowerRequest()
        super.onStop()
    }

    private fun setupSteppers() {
        findViewById<Button>(R.id.btn_thickness_minus).setOnClickListener {
            prefs.thicknessPct -= 5
        }
        findViewById<Button>(R.id.btn_thickness_plus).setOnClickListener {
            prefs.thicknessPct += 5
        }
        findViewById<Button>(R.id.btn_intensity_minus).setOnClickListener {
            prefs.intensityIndex -= 1
        }
        findViewById<Button>(R.id.btn_intensity_plus).setOnClickListener {
            prefs.intensityIndex += 1
        }
        findViewById<Button>(R.id.btn_duration_minus).setOnClickListener {
            prefs.autoOffMinutes = RingLightSettings.previousDuration(prefs.autoOffMinutes)
        }
        findViewById<Button>(R.id.btn_duration_plus).setOnClickListener {
            prefs.autoOffMinutes = RingLightSettings.nextDuration(prefs.autoOffMinutes)
        }
    }

    private fun setupColors() {
        RingLightSettings.COLORS.forEachIndexed { index, color ->
            val swatch = Button(this).apply {
                id = View.generateViewId()
                isFocusable = true
                minWidth = 0
                minHeight = 0
                minimumWidth = 0
                minimumHeight = 0
                setPadding(0, 0, 0, 0)
                textSize = 22f
                setTextColor(if (isLightColor(color)) Color.BLACK else Color.WHITE)
                backgroundTintList = null
                background = swatchBackground(color)
                layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                    if (index > 0) marginStart = dp(4)
                }
                setOnClickListener { prefs.colorIndex = index }
            }
            colorRow.addView(swatch)
        }
        for (index in 0 until colorRow.childCount) {
            colorRow.getChildAt(index).apply {
                if (index > 0) nextFocusLeftId = colorRow.getChildAt(index - 1).id
                if (index < colorRow.childCount - 1) nextFocusRightId = colorRow.getChildAt(index + 1).id
                nextFocusUpId = R.id.btn_toggle
                nextFocusDownId = R.id.btn_thickness_minus
            }
        }
    }

    private fun render() {
        rendering = true
        try {
            val permissionGranted = Settings.canDrawOverlays(this)
            permissionBanner.visibility = if (permissionGranted) View.GONE else View.VISIBLE
            toggleButton.text = getString(
                when (requestedPowerState) {
                    true -> R.string.turning_on
                    false -> R.string.turning_off
                    null -> if (prefs.isOn) R.string.turn_light_off else R.string.turn_light_on
                }
            )
            toggleButton.isEnabled = permissionGranted && requestedPowerState == null
            findViewById<TextView>(R.id.light_status).text = getString(
                when {
                    !permissionGranted -> R.string.permission_needed
                    prefs.isOn -> R.string.light_is_on
                    else -> R.string.light_is_off
                }
            )
            findViewById<View>(R.id.status_dot).backgroundTintList =
                android.content.res.ColorStateList.valueOf(getColor(
                    if (permissionGranted && prefs.isOn) R.color.accent else R.color.text_muted
                ))

            preview.ringColor = RingLightSettings.COLORS[prefs.colorIndex]
            preview.intensity = RingLightSettings.INTENSITY_LEVELS[prefs.intensityIndex]
            preview.thickness = prefs.thickness
            val colorName = RingLightSettings.COLOR_NAMES[prefs.colorIndex]
            findViewById<TextView>(R.id.color_value).text = colorName
            findViewById<TextView>(R.id.preview_summary).text = getString(
                R.string.preview_summary, colorName, RingLightSettings.INTENSITY_LABELS[prefs.intensityIndex]
            )
            preview.contentDescription = getString(
                R.string.preview_description, colorName,
                RingLightSettings.INTENSITY_LABELS[prefs.intensityIndex], prefs.thicknessPct
            )
            for (index in 0 until colorRow.childCount) {
                (colorRow.getChildAt(index) as Button).apply {
                    val selected = index == prefs.colorIndex
                    isSelected = selected
                    text = if (selected) "✓" else ""
                    contentDescription = if (selected) getString(
                        R.string.color_selected, RingLightSettings.COLOR_NAMES[index]
                    ) else RingLightSettings.COLOR_NAMES[index]
                }
            }
            findViewById<TextView>(R.id.thickness_value).text = getString(R.string.percent_value, prefs.thicknessPct)
            findViewById<TextView>(R.id.intensity_value).text = RingLightSettings.INTENSITY_LABELS[prefs.intensityIndex]
            updateStepButtons(
                R.id.btn_thickness_minus, R.id.btn_thickness_plus,
                prefs.thicknessPct > RingLightSettings.MIN_THICKNESS_PCT,
                prefs.thicknessPct < RingLightSettings.MAX_THICKNESS_PCT
            )
            updateStepButtons(
                R.id.btn_intensity_minus, R.id.btn_intensity_plus,
                prefs.intensityIndex > 0,
                prefs.intensityIndex < RingLightSettings.INTENSITY_LEVELS.lastIndex
            )
            autoOffSwitch.isChecked = prefs.autoOffEnabled
            findViewById<TextView>(R.id.duration_value).text = formatDuration(prefs.autoOffMinutes)
            findViewById<View>(R.id.duration_row).alpha = if (prefs.autoOffEnabled) 1f else 0.45f
            updateStepButtons(
                R.id.btn_duration_minus, R.id.btn_duration_plus,
                prefs.autoOffEnabled && prefs.autoOffMinutes > RingLightSettings.DURATION_STEPS.first(),
                prefs.autoOffEnabled && prefs.autoOffMinutes < RingLightSettings.DURATION_STEPS.last()
            )
            renderTimer()
        } finally {
            rendering = false
        }
    }

    private fun updateStepButtons(minusId: Int, plusId: Int, canDecrease: Boolean, canIncrease: Boolean) {
        val minus = findViewById<Button>(minusId)
        val plus = findViewById<Button>(plusId)
        val minusHadFocus = minus.hasFocus()
        val plusHadFocus = plus.hasFocus()
        minus.isEnabled = canDecrease
        plus.isEnabled = canIncrease
        // Keep D-pad focus in the current setting when a step reaches its limit.
        if (minusHadFocus && !canDecrease && canIncrease) plus.requestFocus()
        if (plusHadFocus && !canIncrease && canDecrease) minus.requestFocus()
    }

    private fun renderTimer() {
        val remainingMillis = prefs.autoOffDeadlineMillis - System.currentTimeMillis()
        findViewById<TextView>(R.id.timer_summary).text = when {
            !prefs.autoOffEnabled -> getString(R.string.timer_disabled)
            !prefs.isOn -> getString(R.string.timer_waiting)
            prefs.autoOffDeadlineMillis == 0L -> getString(R.string.timer_starting)
            remainingMillis <= 0 -> getString(R.string.timer_finishing)
            remainingMillis < 60_000L -> resources.getQuantityString(R.plurals.timer_seconds_remaining, ((remainingMillis + 999L) / 1_000L).toInt(), (remainingMillis + 999L) / 1_000L)
            else -> getString(R.string.timer_remaining, formatDuration(((remainingMillis + 59_999L) / 60_000L).toInt()))
        }
    }

    private fun toggleLight() {
        if (requestedPowerState != null) return
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings()
            return
        }
        requestedPowerState = !prefs.isOn
        render()
        if (sendServiceAction(RingLightService.ACTION_TOGGLE)) {
            handler.postDelayed(powerRequestTimeout, 3_000L)
        } else {
            clearPowerRequest()
            render()
        }
    }

    private fun clearPowerRequest() {
        requestedPowerState = null
        handler.removeCallbacks(powerRequestTimeout)
    }

    private fun sendServiceAction(action: String): Boolean = try {
        startForegroundService(Intent(this, RingLightService::class.java).setAction(action))
        true
    } catch (_: IllegalStateException) {
        showMessage(R.string.light_start_failed)
        false
    } catch (_: SecurityException) {
        showMessage(R.string.light_start_failed)
        false
    }

    private fun openOverlaySettings() {
        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Some TV vendors omit the app-specific overlay settings activity.
            } catch (_: SecurityException) {
                // Try the next settings screen if a vendor restricts this one.
            }
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_title)
            .setMessage(R.string.permission_manual_help)
            .setPositiveButton(R.string.done, null)
            .show()
    }

    private fun showRemoteSetup() {
        val command = ShortcutActions.find(prefs.lastShortcutAction)
        val received = if (command == null || prefs.lastShortcutAtMillis == 0L) {
            getString(R.string.shortcuts_no_command)
        } else {
            getString(R.string.shortcuts_last_command, getString(command.label),
                DateUtils.getRelativeTimeSpanString(prefs.lastShortcutAtMillis,
                    System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.shortcuts_title)
            .setMessage(getString(R.string.shortcuts_help) + "\n\n" + received + "\n\n" +
                getString(R.string.shortcuts_diagnostic_help))
            .setNeutralButton(R.string.test_actions) { _, _ ->
                startActivity(Intent(this, ShortcutActivity::class.java)
                    .setAction(ShortcutActivity.ACTION_TEST_SHORTCUT))
            }
            .setPositiveButton(R.string.done, null)
            .show()
    }

    private fun formatDuration(minutes: Int): String = when {
        minutes < 60 -> getString(R.string.duration_minutes, minutes)
        minutes % 60 == 0 -> getString(R.string.duration_hours, minutes / 60)
        else -> getString(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
    }

    private fun swatchBackground(color: Int): StateListDrawable {
        fun layer(focused: Boolean): LayerDrawable {
            val frame = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(if (focused) 0x24FFFFFF else Color.TRANSPARENT)
                if (focused) setStroke(dp(2), getColor(R.color.text_primary))
            }
            val circle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            return LayerDrawable(arrayOf(frame, circle)).apply {
                setLayerInset(1, dp(9), dp(9), dp(9), dp(9))
            }
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), layer(true))
            addState(intArrayOf(android.R.attr.state_pressed), layer(true))
            addState(intArrayOf(), layer(false))
        }
    }

    private fun isLightColor(color: Int): Boolean =
        Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114 > 150

    private fun showMessage(message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
