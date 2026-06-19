package com.ringlight.tv

import android.content.Context
import android.graphics.*
import android.view.View

class RingLightView(context: Context) : View(context) {

    var ringColor: Int = Color.WHITE
        set(value) { field = value; invalidate() }

    var intensity: Float = 1f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    var thickness: Float = 0.15f
        set(value) { field = value.coerceIn(0.04f, 0.45f); invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val t = thickness * minOf(w, h)

        val alpha = (intensity * 255).toInt()
        val r = Color.red(ringColor)
        val g = Color.green(ringColor)
        val b = Color.blue(ringColor)
        val solid = Color.argb(alpha, r, g, b)
        val clear = Color.TRANSPARENT

        // top edge strip
        paint.shader = LinearGradient(0f, 0f, 0f, t, solid, clear, Shader.TileMode.CLAMP)
        canvas.drawRect(t, 0f, w - t, t, paint)

        // bottom edge strip
        paint.shader = LinearGradient(0f, h - t, 0f, h, clear, solid, Shader.TileMode.CLAMP)
        canvas.drawRect(t, h - t, w - t, h, paint)

        // left edge strip
        paint.shader = LinearGradient(0f, 0f, t, 0f, solid, clear, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, t, t, h - t, paint)

        // right edge strip
        paint.shader = LinearGradient(w - t, 0f, w, 0f, clear, solid, Shader.TileMode.CLAMP)
        canvas.drawRect(w - t, t, w, h - t, paint)

        // top-left corner — radial gradient centred on the screen corner
        paint.shader = RadialGradient(0f, 0f, t, solid, clear, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, t, t, paint)

        // top-right corner
        paint.shader = RadialGradient(w, 0f, t, solid, clear, Shader.TileMode.CLAMP)
        canvas.drawRect(w - t, 0f, w, t, paint)

        // bottom-left corner
        paint.shader = RadialGradient(0f, h, t, solid, clear, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, h - t, t, h, paint)

        // bottom-right corner
        paint.shader = RadialGradient(w, h, t, solid, clear, Shader.TileMode.CLAMP)
        canvas.drawRect(w - t, h - t, w, h, paint)
    }
}
