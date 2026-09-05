package com.ringlight.tv

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowWindowManagerImpl

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 28, 34])
class RingLightServiceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var prefs: Prefs
    private var controller: ServiceController<RingLightService>? = null
    private lateinit var service: RingLightService

    @Before fun setup() {
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(context)
        ShadowSettings.setCanDrawOverlays(true)
        Settings.Global.putInt(context.contentResolver, Settings.Global.BOOT_COUNT, 1)
        createService()
    }

    private fun createService() {
        controller = Robolectric.buildService(RingLightService::class.java).create()
        service = controller!!.get()
    }

    @After fun teardown() { controller?.destroy() }
    private fun command(action: String?) = service.onStartCommand(
        action?.let { Intent(context, RingLightService::class.java).setAction(it) }, 0, 1)
    private fun windows(): ShadowWindowManagerImpl = Shadow.extract(service.getSystemService(WindowManager::class.java))

    @Test fun `an idle service stops without posting an ongoing notification`() {
        assertEquals(Service.START_NOT_STICKY, command(null))
        assertNull(shadowOf(service).lastForegroundNotification)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertFalse(prefs.isOn)
    }

    @Test fun `turning on creates exactly one overlay that cannot capture remote focus`() {
        assertEquals(Service.START_STICKY, command(RingLightService.ACTION_TOGGLE))
        assertTrue(prefs.isOn)
        assertNotNull(shadowOf(service).lastForegroundNotification)
        val view = windows().views.single()
        val params = view.layoutParams as WindowManager.LayoutParams
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        command(RingLightService.ACTION_REFRESH)
        assertEquals(1, windows().views.size)
    }

    @Test fun `turn off is idempotent and removes overlay timer and notification`() {
        command(RingLightService.ACTION_TOGGLE)
        command(RingLightService.ACTION_TURN_OFF)
        command(RingLightService.ACTION_TURN_OFF)
        assertFalse(prefs.isOn)
        assertTrue(windows().views.isEmpty())
        assertTrue(shadowOf(service).isForegroundStopped)
        assertEquals(0L, prefs.autoOffDeadlineMillis)
    }

    @Test fun `notification always sends turn off rather than toggling`() {
        command(RingLightService.ACTION_TOGGLE)
        val pending = shadowOf(service).lastForegroundNotification.actions.single().actionIntent
        assertEquals(RingLightService.ACTION_TURN_OFF, shadowOf(pending).savedIntent.action)
    }

    @Test fun `denied overlay permission leaves no active session`() {
        ShadowSettings.setCanDrawOverlays(false)
        assertEquals(Service.START_NOT_STICKY, command(RingLightService.ACTION_TOGGLE))
        assertFalse(prefs.isOn)
        assertTrue(windows().views.isEmpty())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test fun `permission revocation is detected without another user command`() {
        command(RingLightService.ACTION_TOGGLE)
        ShadowSettings.setCanDrawOverlays(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(31))
        assertFalse(prefs.isOn)
        assertTrue(windows().views.isEmpty())
    }

    @Test fun `changing timer length while on takes effect immediately`() {
        command(RingLightService.ACTION_TOGGLE)
        prefs.autoOffMinutes = 5
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(5))
        assertFalse(prefs.isOn)
        assertTrue(windows().views.isEmpty())
    }

    @Test fun `disabling timer prevents the previously scheduled turn off`() {
        prefs.autoOffMinutes = 5
        command(RingLightService.ACTION_TOGGLE)
        prefs.autoOffEnabled = false
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(6))
        assertTrue(prefs.isOn)
        assertEquals(0L, prefs.autoOffDeadlineMillis)
    }

    @Test fun `appearance edits do not reset a running timer`() {
        prefs.autoOffMinutes = 5
        command(RingLightService.ACTION_TOGGLE)
        val deadline = prefs.autoOffElapsedDeadlineMillis
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(2))
        prefs.colorIndex = 6
        prefs.thicknessPct = 35
        assertEquals(deadline, prefs.autoOffElapsedDeadlineMillis)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(3))
        assertFalse(prefs.isOn)
    }

    @Test fun `system destruction preserves the active request for sticky restart`() {
        command(RingLightService.ACTION_TOGGLE)
        val deadline = prefs.autoOffDeadlineMillis
        val elapsedDeadline = prefs.autoOffElapsedDeadlineMillis
        controller!!.destroy()
        controller = null
        assertTrue(prefs.isOn)
        assertEquals(deadline, prefs.autoOffDeadlineMillis)
        createService()
        command(null)
        assertEquals(1, windows().views.size)
        assertEquals(elapsedDeadline, prefs.autoOffElapsedDeadlineMillis)
    }

    @Test fun `colour cycle while off saves the colour and leaves no service running`() {
        prefs.colorIndex = 7
        assertEquals(Service.START_NOT_STICKY, command(RingLightService.ACTION_CYCLE_COLOR))
        assertEquals(0, prefs.colorIndex)
        assertFalse(prefs.isOn)
        assertTrue(shadowOf(service).isStoppedBySelf)
    }
}
