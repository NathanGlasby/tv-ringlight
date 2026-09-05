package com.ringlight.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.roundToInt

/** The same renderer powers the preview and the non-interactive screen overlay. */
class RingLightView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private data class Strip(val bounds: RectF, val shader: Shader)

    private val paint = Paint()
    private var strips: List<Strip> = emptyList()

    var ringColor: Int = RingLightSettings.COLORS[RingLightSettings.DEFAULT_COLOR_INDEX]
        set(value) {
            if (field == value) return
            field = value
            rebuildGradients()
            invalidate()
        }

    var intensity: Float = 1f
        set(value) {
            val bounded = if (value.isFinite()) value.coerceIn(0f, 1f) else 1f
            if (field == bounded) return
            field = bounded
            invalidate()
        }

    var thickness: Float = RingLightSettings.DEFAULT_THICKNESS_PCT / 100f
        set(value) {
            val bounded = if (value.isFinite()) {
                value.coerceIn(RingLightSettings.MIN_THICKNESS_PCT / 100f, RingLightSettings.MAX_THICKNESS_PCT / 100f)
            } else {
                RingLightSettings.DEFAULT_THICKNESS_PCT / 100f
            }
            if (field == bounded) return
            field = bounded
            rebuildGradients()
            invalidate()
        }

    init {
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildGradients()
    }

    private fun rebuildGradients() {
        val w = width.toFloat()
        val h = height.toFloat()
        val t = thickness * minOf(w, h)
        if (t <= 0f) {
            strips = emptyList()
            return
        }

        val solid = ringColor or (0xFF shl 24)
        val clear = ringColor and 0x00FFFFFF
        val clamp = Shader.TileMode.CLAMP

        // Each corner starts at its inner edge. Its boundary exactly matches the
        // neighbouring straight gradients, avoiding dark notches at the joins.
        strips = listOf(
            Strip(RectF(t, 0f, w - t, t), LinearGradient(0f, 0f, 0f, t, solid, clear, clamp)),
            Strip(RectF(t, h - t, w - t, h), LinearGradient(0f, h - t, 0f, h, clear, solid, clamp)),
            Strip(RectF(0f, t, t, h - t), LinearGradient(0f, 0f, t, 0f, solid, clear, clamp)),
            Strip(RectF(w - t, t, w, h - t), LinearGradient(w - t, 0f, w, 0f, clear, solid, clamp)),
            Strip(RectF(0f, 0f, t, t), RadialGradient(t, t, t, clear, solid, clamp)),
            Strip(RectF(w - t, 0f, w, t), RadialGradient(w - t, t, t, clear, solid, clamp)),
            Strip(RectF(0f, h - t, t, h), RadialGradient(t, h - t, t, clear, solid, clamp)),
            Strip(RectF(w - t, h - t, w, h), RadialGradient(w - t, h - t, t, clear, solid, clamp)),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (intensity == 0f) return
        paint.alpha = (intensity * Color.alpha(ringColor)).roundToInt()
        for (strip in strips) {
            paint.shader = strip.shader
            canvas.drawRect(strip.bounds, paint)
        }
    }
}
