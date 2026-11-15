package com.peng.pdfreader.feature.lock.sheet

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * The 14x14 error marker of the password sheet.
 *
 * The design shows a small red disc with a white exclamation mark next to the
 * error message. It is drawn in code because the sheet is the only place that
 * needs it.
 */
class LockErrorIconDrawable : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()

    override fun draw(canvas: Canvas) {
        bounds.set(getBounds())
        val radius = minOf(bounds.width(), bounds.height()) / 2f
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()

        paint.color = ERROR_COLOR
        canvas.drawCircle(centerX, centerY, radius, paint)

        paint.color = Color.WHITE
        canvas.drawRoundRect(
            centerX - radius * 0.13f,
            centerY - radius * 0.52f,
            centerX + radius * 0.13f,
            centerY + radius * 0.16f,
            radius * 0.13f,
            radius * 0.13f,
            paint,
        )
        canvas.drawCircle(centerX, centerY + radius * 0.44f, radius * 0.14f, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        /** #fb4141 - the light brand colour of the design system. */
        const val ERROR_COLOR = 0xFFFB4141.toInt()
    }
}
