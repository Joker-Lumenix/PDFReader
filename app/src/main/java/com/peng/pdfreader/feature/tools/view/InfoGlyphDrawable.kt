package com.peng.pdfreader.feature.tools.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * The small information mark of the blue banner of the split screen.
 *
 * The design draws it as a 14x14 white glyph (a ring with a dot and a bar),
 * there is no asset for it, so it is painted here.
 */
class InfoGlyphDrawable(strokeWidthPx: Float) : Drawable() {

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return
        val size = minOf(bounds.width(), bounds.height()).toFloat()
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        val dot = size * 0.09f

        canvas.drawCircle(centerX, centerY, size / 2f - stroke.strokeWidth, stroke)
        canvas.drawCircle(centerX, centerY - size * 0.26f, dot, fill)
        canvas.drawRoundRect(
            RectF(
                centerX - dot,
                centerY + size * 0.02f,
                centerX + dot,
                centerY + size * 0.30f,
            ),
            dot,
            dot,
            fill,
        )
    }

    override fun setAlpha(alpha: Int) {
        stroke.alpha = alpha
        fill.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        stroke.colorFilter = colorFilter
        fill.colorFilter = colorFilter
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
