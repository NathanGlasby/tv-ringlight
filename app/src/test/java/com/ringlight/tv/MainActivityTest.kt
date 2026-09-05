package com.ringlight.tv

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-television-mdpi")
class MainActivityTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var prefs: Prefs
    private var controller: ActivityController<MainActivity>? = null
    private val activity get() = controller!!.get()
    private fun start() { controller = Robolectric.buildActivity(MainActivity::class.java).setup().visible() }
    private fun button(id: Int) = activity.findViewById<Button>(id)

    @Before fun setup() {
        context.getSharedPreferences(Prefs.FILE_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(context)
        ShadowSettings.setCanDrawOverlays(true)
    }
    @After fun teardown() { controller?.pause()?.stop()?.destroy() }

    @Test fun `opening settings keeps an inactive light and service off`() {
        start()
        assertFalse(prefs.isOn)
        assertNull(shadowOf(context).nextStartedService)
        assertEquals("Turn light on", button(R.id.btn_toggle).text.toString())
    }

    @Test fun `appearance changes are saved and previewed without starting the service`() {
        start()
        button(R.id.btn_thickness_plus).performClick()
        button(R.id.btn_intensity_minus).performClick()
        activity.findViewById<LinearLayout>(R.id.color_row).getChildAt(6).performClick()
        val preview = activity.findViewById<RingLightView>(R.id.light_preview)
        assertEquals(0.20f, preview.thickness, 0f)
        assertEquals(0.75f, preview.intensity, 0f)
        assertEquals(RingLightSettings.COLORS[6], preview.ringColor)
        assertNull(shadowOf(context).nextStartedService)
    }

    @Test fun `remote changes update selected colour and displayed power state`() {
        start()
        val other = Prefs(context)
        other.colorIndex = 5
        other.isOn = true
        assertEquals("Blue", activity.findViewById<TextView>(R.id.color_value).text.toString())
        assertTrue(activity.findViewById<LinearLayout>(R.id.color_row).getChildAt(5).isSelected)
        assertEquals("Turn light off", button(R.id.btn_toggle).text.toString())
    }

    @Test fun `width stepper stops at bounds and transfers remote focus to its usable button`() {
        prefs.thicknessPct = 35
        start()
        button(R.id.btn_thickness_plus).requestFocus()
        button(R.id.btn_thickness_plus).performClick()
        assertEquals(40, prefs.thicknessPct)
        assertFalse(button(R.id.btn_thickness_plus).isEnabled)
        assertTrue(button(R.id.btn_thickness_minus).hasFocus())
    }

    @Test fun `disabling timer disables duration controls`() {
        start()
        activity.findViewById<SwitchCompat>(R.id.auto_off_switch).isChecked = false
        assertFalse(prefs.autoOffEnabled)
        assertFalse(button(R.id.btn_duration_minus).isEnabled)
        assertFalse(button(R.id.btn_duration_plus).isEnabled)
    }

    @Test fun `missing permission focuses setup and keeps power disabled`() {
        ShadowSettings.setCanDrawOverlays(false)
        start()
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.permission_banner).visibility)
        assertFalse(button(R.id.btn_toggle).isEnabled)
        assertTrue(button(R.id.btn_grant_permission).hasFocus())
    }

    @Test fun `toggle waits for the service state before claiming the light is on`() {
        start()
        button(R.id.btn_toggle).performClick()
        assertFalse(prefs.isOn)
        assertFalse(button(R.id.btn_toggle).isEnabled)
        assertEquals("Turning on…", button(R.id.btn_toggle).text.toString())
        prefs.isOn = true
        assertTrue(button(R.id.btn_toggle).isEnabled)
        assertEquals("Turn light off", button(R.id.btn_toggle).text.toString())
    }

    @Test fun `all eight colour controls fit inside the TV settings column`() {
        start()
        val decor = activity.window.decorView
        decor.measure(View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(540, View.MeasureSpec.EXACTLY))
        decor.layout(0, 0, 960, 540)
        shadowOf(Looper.getMainLooper()).idle()
        val row = activity.findViewById<LinearLayout>(R.id.color_row)
        assertEquals(8, row.childCount)
        assertTrue(row.width > 0)
        for (index in 0 until row.childCount) {
            val child = row.getChildAt(index)
            assertTrue("Colour $index must be visible", child.width >= 40)
            assertTrue(child.left >= 0 && child.right <= row.width)
        }
        assertTrue(activity.findViewById<View>(R.id.light_preview).width > 0)
    }
}
