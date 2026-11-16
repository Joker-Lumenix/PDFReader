package com.peng.pdfreader.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * The 24dp lock badge of the design.
 *
 * The file rows and the result card mark an encrypted document with a red
 * corner badge that carries a white padlock. It is drawn in code because the
 * lock flow is the only place that needs it.
 */
class LockBadgeDrawable(context: Context) : Drawable() {

    private val res = context.resources
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shape = Path()
    private val bounds = RectF()

    override fun draw(canvas: Canvas) {
        bounds.set(getBounds())
        val size = minOf(bounds.width(), bounds.height())
        if (size <= 0f) return

        // The badge is a rectangle whose bottom right corner is a quarter disc.
        val corner = dp(4).toFloat()
        shape.reset()
        shape.moveTo(bounds.left + corner, bounds.top)
        shape.lineTo(bounds.right, bounds.top)
        shape.lineTo(bounds.right, bounds.bottom - size)
        shape.quadTo(bounds.right, bounds.bottom, bounds.right - size, bounds.bottom)
        shape.lineTo(bounds.left, bounds.bottom)
        shape.lineTo(bounds.left, bounds.top + corner)
        shape.quadTo(bounds.left, bounds.top, bounds.left + corner, bounds.top)
        shape.close()

        paint.color = BADGE_COLOR
        paint.style = Paint.Style.FILL
        canvas.drawPath(shape, paint)

        drawLock(canvas, bounds.centerX(), bounds.centerY(), size)
    }

    /** The white padlock of the design: a shackle over a small body. */
    private fun drawLock(canvas: Canvas, centerX: Float, centerY: Float, size: Float) {
        val bodyWidth = size * 0.38f
        val bodyHeight = size * 0.34f
        val bodyTop = centerY - size * 0.04f

        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.07f
        val shackle = RectF(
            centerX - bodyWidth / 2f,
            bodyTop - size * 0.34f,
            centerX + bodyWidth / 2f,
            bodyTop + size * 0.08f,
        )
        canvas.drawArc(shackle, 180f, 180f, false, paint)

        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(
            centerX - bodyWidth / 2f,
            bodyTop,
            centerX + bodyWidth / 2f,
            bodyTop + bodyHeight,
            size * 0.06f,
            size * 0.06f,
            paint,
        )
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = dp(24)

    override fun getIntrinsicHeight(): Int = dp(24)

    private fun dp(value: Int): Int =
        (value * res.displayMetrics.density).toInt()

    private companion object {
        /** #ee3f5d - the badge colour of the design. */
        const val BADGE_COLOR = 0xFFEE3F5D.toInt()
    }
}
