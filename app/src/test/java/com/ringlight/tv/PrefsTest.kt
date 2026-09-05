package com.ringlight.tv

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PrefsTest {
    private lateinit var storage: SharedPreferences
    private lateinit var prefs: Prefs

    @Before fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        storage = context.getSharedPreferences("ringlight", Context.MODE_PRIVATE)
        storage.edit().clear().commit()
        prefs = Prefs(context)
    }

    @Test fun `new install starts off with a three hour timer`() {
        assertFalse(prefs.isOn)
        assertTrue(prefs.autoOffEnabled)
        assertEquals(180, prefs.autoOffMinutes)
        assertEquals(0L, prefs.autoOffDeadlineMillis)
    }

    @Test fun `settings from version one survive the rebuild`() {
        storage.edit()
            .putBoolean("is_on", true)
            .putInt("color_index", 6)
            .putInt("intensity_index", 1)
            .putInt("thickness_pct", 25)
            .putBoolean("auto_off_enabled", false)
            .putInt("auto_off_minutes", 90)
            .commit()

        assertTrue(prefs.isOn)
        assertEquals(6, prefs.colorIndex)
        assertEquals(1, prefs.intensityIndex)
        assertEquals(0.25f, prefs.thickness, 0f)
        assertFalse(prefs.autoOffEnabled)
        assertEquals(90, prefs.autoOffMinutes)
    }

    @Test fun `out of range saved values cannot crash palette access or schedule invalid timers`() {
        storage.edit()
            .putInt("color_index", Int.MAX_VALUE)
            .putInt("intensity_index", Int.MIN_VALUE)
            .putInt("thickness_pct", Int.MAX_VALUE)
            .putInt("auto_off_minutes", Int.MIN_VALUE)
            .commit()

        assertEquals(0xFFFF4DC8.toInt(), RingLightSettings.COLORS[prefs.colorIndex])
        assertEquals(0.25f, RingLightSettings.INTENSITY_LEVELS[prefs.intensityIndex], 0f)
        assertEquals(40, prefs.thicknessPct)
        assertEquals(5, prefs.autoOffMinutes)
    }

    @Test fun `incorrect preference types fall back without crashing startup`() {
        storage.edit()
            .putString("is_on", "true")
            .putString("color_index", "blue")
            .putFloat("intensity_index", 0.5f)
            .putBoolean("thickness_pct", true)
            .putInt("auto_off_enabled", 1)
            .putString("auto_off_minutes", "forever")
            .putString("auto_off_deadline_millis", "tomorrow")
            .commit()

        assertFalse(prefs.isOn)
        assertEquals(0, prefs.colorIndex)
        assertEquals(3, prefs.intensityIndex)
        assertEquals(15, prefs.thicknessPct)
        assertTrue(prefs.autoOffEnabled)
        assertEquals(180, prefs.autoOffMinutes)
        assertEquals(0L, prefs.autoOffDeadlineMillis)
    }

    @Test fun `writes keep every setting within usable limits`() {
        prefs.colorIndex = -1
        prefs.intensityIndex = 42
        prefs.thicknessPct = -1
        prefs.autoOffMinutes = Int.MAX_VALUE
        assertEquals(0, prefs.colorIndex)
        assertEquals(3, prefs.intensityIndex)
        assertEquals(5, prefs.thicknessPct)
        assertEquals(480, prefs.autoOffMinutes)
    }

    @Test fun `reset restores controls without switching off an active session`() {
        prefs.isOn = true
        prefs.colorIndex = 4
        prefs.intensityIndex = 1
        prefs.thicknessPct = 40
        prefs.autoOffEnabled = false
        prefs.autoOffMinutes = 5
        prefs.setAutoOffDeadline(100_000L, 40_000L, 3)

        prefs.resetSettings()

        assertTrue(prefs.isOn)
        assertEquals(0, prefs.colorIndex)
        assertEquals(3, prefs.intensityIndex)
        assertEquals(15, prefs.thicknessPct)
        assertTrue(prefs.autoOffEnabled)
        assertEquals(180, prefs.autoOffMinutes)
        assertEquals(100_000L, prefs.autoOffDeadlineMillis)
        assertEquals(40_000L, prefs.autoOffElapsedDeadlineMillis)
        assertEquals(3, prefs.autoOffBootCount)
    }

    @Test fun `deadline updates are observed as one consistent record`() {
        val observations = mutableListOf<Triple<Long, Long, Int>>()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            observations.add(Triple(prefs.autoOffDeadlineMillis, prefs.autoOffElapsedDeadlineMillis, prefs.autoOffBootCount))
        }
        prefs.registerListener(listener)
        prefs.setAutoOffDeadline(100_000L, 40_000L, 3)
        assertTrue(observations.isNotEmpty())
        assertTrue(observations.all { it == Triple(100_000L, 40_000L, 3) })
        prefs.unregisterListener(listener)
    }

    @Test fun `separate activity and service instances observe each others changes`() {
        val other = Prefs(RuntimeEnvironment.getApplication())
        val changes = mutableListOf<String?>()
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> changes.add(key) }
        other.registerListener(listener)
        prefs.colorIndex = 2
        assertEquals(2, other.colorIndex)
        assertTrue(changes.contains("color_index"))
        other.unregisterListener(listener)
        changes.clear()
        prefs.colorIndex = 3
        assertTrue(changes.isEmpty())
    }

    @Test fun `turning off clears both clocks and reboot metadata`() {
        prefs.setAutoOffDeadline(100_000L, 40_000L, 3)
        prefs.clearAutoOffDeadline()
        assertEquals(0L, prefs.autoOffDeadlineMillis)
        assertEquals(0L, prefs.autoOffElapsedDeadlineMillis)
        assertEquals(-1, prefs.autoOffBootCount)
    }
}
