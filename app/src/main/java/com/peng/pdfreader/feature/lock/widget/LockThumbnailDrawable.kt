package com.peng.pdfreader.feature.lock.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable

/**
 * The thumbnail of a document in the lock flow.
 *
 * The design shows the file logo, the page count on a dark chip in the lower
 * right corner and - for an encrypted document - the red lock badge in the
 * upper left corner.
 */
class LockThumbnailDrawable(
    context: Context,
    private val base: Drawable,
    private val pageCount: Int,
    private val locked: Boolean,
) : Drawable() {

    private val res = context.resources
    private val badge = LockBadgeDrawable(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chip = RectF()

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return

        // The logo keeps its ratio and sits in the middle of the thumbnail.
        val size = minOf(bounds.width(), bounds.height())
        val left = bounds.left + (bounds.width() - size) / 2f
        val top = bounds.top + (bounds.height() - size) / 2f
        base.setBounds(left.toInt(), top.toInt(), (left + size).toInt(), (top + size).toInt())
        base.draw(canvas)

        if (locked) {
            badge.setBounds(bounds.left, bounds.top, bounds.left + dp(24), bounds.top + dp(24))
            badge.draw(canvas)
        }

        if (pageCount > 0) drawPageCount(canvas, RectF(bounds))
    }

    /** The dark chip with the page count in the lower right corner. */
    private fun drawPageCount(canvas: Canvas, bounds: RectF) {
        val width = dp(28).toFloat()
        val height = dp(14).toFloat()
        chip.set(bounds.right - width, bounds.bottom - height, bounds.right, bounds.bottom)

        paint.color = CHIP_COLOR
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(chip, dp(4).toFloat(), dp(4).toFloat(), paint)

        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = dp(10).toFloat()
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val metrics = paint.fontMetrics
        val baseline = chip.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(pageCount.toString(), chip.centerX(), baseline, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        badge.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        badge.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = dp(48)

    override fun getIntrinsicHeight(): Int = dp(48)

    private fun dp(value: Int): Int =
        (value * res.displayMetrics.density).toInt()

    private companion object {
        /** rgba(0,0,0,0.45) - the chip colour of the design. */
        const val CHIP_COLOR = 0x73000000
    }
}
