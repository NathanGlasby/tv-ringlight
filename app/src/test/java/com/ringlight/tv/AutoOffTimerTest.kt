package com.ringlight.tv

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AutoOffTimerTest {
    private lateinit var prefs: Prefs
    private var wall = 1_000_000L
    private var elapsed = 10_000L
    private var boot = 4
    private val context get() = RuntimeEnvironment.getApplication()
    private fun timer() = AutoOffTimer(context, prefs, { wall }, { elapsed }, { boot })

    @Before fun setup() {
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(context)
        prefs.autoOffMinutes = 5
    }

    @Test fun `starting creates a five minute deadline on both clocks`() {
        assertEquals(300_000L, timer().synchronize(true))
        assertEquals(1_300_000L, prefs.autoOffDeadlineMillis)
        assertEquals(310_000L, prefs.autoOffElapsedDeadlineMillis)
        assertEquals(4, prefs.autoOffBootCount)
    }

    @Test fun `process restart preserves elapsed session time`() {
        timer().synchronize(true)
        elapsed += 120_000L
        wall += 120_000L
        assertEquals(180_000L, timer().synchronize(false))
        assertEquals(1_300_000L, prefs.autoOffDeadlineMillis)
    }

    @Test fun `changing colour or width does not restart the timer`() {
        timer().synchronize(true)
        elapsed += 120_000L
        wall += 120_000L
        prefs.colorIndex = 5
        prefs.thicknessPct = 30
        assertEquals(180_000L, timer().synchronize(false))
    }

    @Test fun `moving the TV clock backwards does not extend this session`() {
        timer().synchronize(true)
        elapsed += 120_000L
        wall -= 3_600_000L
        assertEquals(180_000L, timer().remainingMillis())
    }

    @Test fun `reboot uses the stored wall deadline instead of the old boot clock`() {
        timer().synchronize(true)
        boot++
        elapsed = 500L
        wall += 240_000L
        assertEquals(60_000L, timer().synchronize(false))
        assertEquals(60_500L, prefs.autoOffElapsedDeadlineMillis)
        assertEquals(5, prefs.autoOffBootCount)
    }

    @Test fun `expired session stays expired after reboot`() {
        timer().synchronize(true)
        boot++
        elapsed = 500L
        wall += 400_000L
        assertTrue(timer().synchronize(false)!! < 0L)
    }

    @Test fun `changing timer settings explicitly starts the chosen duration`() {
        timer().synchronize(true)
        elapsed += 120_000L
        wall += 120_000L
        prefs.autoOffMinutes = 10
        assertEquals(600_000L, timer().synchronize(true))
    }

    @Test fun `disabling timer clears saved deadlines and enabling starts a fresh timer`() {
        timer().synchronize(true)
        prefs.autoOffEnabled = false
        assertNull(timer().synchronize(false))
        assertEquals(0L, prefs.autoOffDeadlineMillis)
        prefs.autoOffEnabled = true
        assertEquals(300_000L, timer().synchronize(false))
    }

    @Test fun `firmware without boot count uses wall time and never extends beyond configured duration`() {
        boot = -1
        timer().synchronize(true)
        wall += 60_000L
        assertEquals(240_000L, timer().remainingMillis())
        wall -= 3_600_000L
        assertEquals(300_000L, timer().remainingMillis())
    }
}
