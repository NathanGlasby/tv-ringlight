package com.ringlight.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AlertDialog
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], qualifiers = "w960dp-h540dp-land-television-mdpi")
class ShortcutIntegrationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var prefs: Prefs

    @Before fun setup() {
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(context)
        ShadowSettings.setCanDrawOverlays(true)
    }

    @Test fun `Button Mapper can discover the exported legacy shortcut picker`() {
        val resolved = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_CREATE_SHORTCUT).setPackage(context.packageName), PackageManager.MATCH_DEFAULT_ONLY)
        assertTrue(resolved.any { it.activityInfo.name == ShortcutActivity::class.java.name && it.activityInfo.exported })
    }

    @Suppress("DEPRECATION")
    @Test fun `each picker choice returns the correct explicit command without running it`() {
        for ((index, command) in ShortcutActions.commands.withIndex()) {
            val controller = Robolectric.buildActivity(ShortcutActivity::class.java,
                Intent(Intent.ACTION_CREATE_SHORTCUT)).setup()
            val activity = controller.get()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            val list = dialog.listView
            list.performItemClick(list.getChildAt(index), index, list.adapter.getItemId(index))
            val result = shadowOf(activity)
            assertEquals(Activity.RESULT_OK, result.resultCode)
            val intent = result.resultIntent.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)!!
            assertEquals(command.action, intent.action)
            assertEquals(TrampolineActivity::class.java.name, intent.component!!.className)
            assertEquals(context.getString(command.label), result.resultIntent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME))
            assertFalse(prefs.isOn)
            assertNull(prefs.lastShortcutAction)
            controller.pause().stop().destroy()
        }
    }

    @Test fun `canceling the picker returns no assignment and runs nothing`() {
        val controller = Robolectric.buildActivity(ShortcutActivity::class.java,
            Intent(Intent.ACTION_CREATE_SHORTCUT)).setup()
        (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        assertEquals(Activity.RESULT_CANCELED, shadowOf(controller.get()).resultCode)
        assertNull(shadowOf(controller.get()).resultIntent)
        assertNull(shadowOf(context).nextStartedService)
        controller.pause().stop().destroy()
    }

    @Test fun `mapped toggle records receipt and dispatches to the overlay service`() {
        val controller = Robolectric.buildActivity(TrampolineActivity::class.java,
            Intent(RingLightService.ACTION_TOGGLE)).setup()
        assertEquals(RingLightService.ACTION_TOGGLE, shadowOf(context).nextStartedService.action)
        assertEquals(RingLightService.ACTION_TOGGLE, prefs.lastShortcutAction)
        assertTrue(prefs.lastShortcutAtMillis > 0L)
        assertTrue(controller.get().isFinishing)
        controller.pause().stop().destroy()
    }

    @Test fun `mapped toggle without permission opens setup and does not silently start a service`() {
        ShadowSettings.setCanDrawOverlays(false)
        val controller = Robolectric.buildActivity(TrampolineActivity::class.java,
            Intent(RingLightService.ACTION_TOGGLE)).setup()
        assertEquals(MainActivity::class.java.name, shadowOf(context).nextStartedActivity.component!!.className)
        assertNull(shadowOf(context).nextStartedService)
        assertEquals(RingLightService.ACTION_TOGGLE, prefs.lastShortcutAction)
        controller.pause().stop().destroy()
    }

    @Test fun `unknown external actions cannot reach internal refresh or timer commands`() {
        val controller = Robolectric.buildActivity(TrampolineActivity::class.java,
            Intent(RingLightService.ACTION_TIMER_CHANGED)).setup()
        assertTrue(controller.get().isFinishing)
        assertNull(shadowOf(context).nextStartedService)
        assertNull(prefs.lastShortcutAction)
        controller.pause().stop().destroy()
    }

    @Test fun `inactive colour shortcuts cycle without an idle background service`() {
        prefs.colorIndex = 7
        val controller = Robolectric.buildActivity(TrampolineActivity::class.java,
            Intent(RingLightService.ACTION_CYCLE_COLOR)).setup()
        assertEquals(0, prefs.colorIndex)
        assertNull(shadowOf(context).nextStartedService)
        assertFalse(prefs.isOn)
        controller.pause().stop().destroy()
    }

    @Test fun `remote test chooser runs through the same exported entry point`() {
        val controller = Robolectric.buildActivity(ShortcutActivity::class.java,
            Intent(ShortcutActivity.ACTION_TEST_SHORTCUT)).setup()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.listView.performItemClick(dialog.listView.getChildAt(0), 0, 0)
        val intent = shadowOf(context).nextStartedActivity
        assertEquals(TrampolineActivity::class.java.name, intent.component!!.className)
        assertEquals(RingLightService.ACTION_TOGGLE, intent.action)
        controller.pause().stop().destroy()
    }

    @Test fun `boot does not start anything when the light is off`() {
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertNull(shadowOf(context).nextStartedService)
    }

    @Test fun `boot restores an active light with remaining time`() {
        prefs.isOn = true
        prefs.autoOffDeadlineMillis = System.currentTimeMillis() + 60_000L
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(RingLightService::class.java.name, shadowOf(context).nextStartedService.component!!.className)
    }

    @Test fun `boot clears an expired session instead of relighting the screen`() {
        prefs.isOn = true
        prefs.autoOffDeadlineMillis = System.currentTimeMillis() - 1L
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertFalse(prefs.isOn)
        assertNull(shadowOf(context).nextStartedService)
    }

    @Test fun `boot clears active state if overlay permission was removed`() {
        prefs.isOn = true
        ShadowSettings.setCanDrawOverlays(false)
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertFalse(prefs.isOn)
        assertNull(shadowOf(context).nextStartedService)
    }
}
