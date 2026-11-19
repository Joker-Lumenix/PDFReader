package com.peng.pdfreader.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.peng.pdfreader.R

/**
 * The thumbnail of a document in the lock flow.
 *
 * The design shows the page count on a dark chip in the lower right corner.
 * An encrypted document shows its page blurred - the content cannot be read
 * any more - and the red lock badge of the design in the upper left corner.
 */
class LockThumbnailDrawable(
    context: Context,
    private val base: Drawable?,
    private val pageCount: Int,
    private val locked: Boolean,
) : Drawable() {

    private val res = context.resources
    private val badge: Drawable? = ContextCompat.getDrawable(context, R.mipmap.ic_lock_corner)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chip = RectF()

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return

        if (locked) {
            // The page of an encrypted document is blurred.
            drawBlurredPage(canvas, RectF(bounds))
            // The lock badge of the design sits in the upper left corner.
            badge?.let {
                val size = dp(24).toFloat()
                it.setBounds(
                    bounds.left.toInt(),
                    bounds.top.toInt(),
                    (bounds.left + size).toInt(),
                    (bounds.top + size).toInt(),
                )
                it.draw(canvas)
            }
        } else {
            // The logo keeps its ratio and sits in the middle of the thumbnail.
            base?.let { logo ->
                val size = minOf(bounds.width(), bounds.height())
                val left = bounds.left + (bounds.width() - size) / 2f
                val top = bounds.top + (bounds.height() - size) / 2f
                logo.setBounds(left.toInt(), top.toInt(), (left + size).toInt(), (top + size).toInt())
                logo.draw(canvas)
            }
        }

        if (pageCount > 0) drawPageCount(canvas, RectF(bounds))
    }

    /**
     * The blurred page of the design.
     *
     * The content of an encrypted document cannot be read, so the page is
     * drawn as a grey surface with a few soft bands on it.
     */
    private fun drawBlurredPage(canvas: Canvas, bounds: RectF) {
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            BLUR_TOP,
            BLUR_BOTTOM,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(bounds, paint)
        paint.shader = null

        // The soft bands are the blurred lines of the page.
        paint.color = BLUR_BAND
        paint.style = Paint.Style.FILL
        val bandHeight = bounds.height() * 0.07f
        var y = bounds.top + bounds.height() * 0.22f
        var index = 0
        while (y < bounds.bottom * 0.86f) {
            val width = bounds.width() * (if (index % 3 == 2) 0.48f else 0.72f)
            canvas.drawRoundRect(
                bounds.left + bounds.width() * 0.12f,
                y,
                bounds.left + bounds.width() * 0.12f + width,
                y + bandHeight,
                bandHeight / 2f,
                bandHeight / 2f,
                paint,
            )
            y += bandHeight * 2.1f
            index++
        }
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
        badge?.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        badge?.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = dp(48)

    override fun getIntrinsicHeight(): Int = dp(48)

    private fun dp(value: Int): Int =
        (value * res.displayMetrics.density).toInt()

    private companion object {
        /** rgba(0,0,0,0.45) - the chip colour of the design. */
        const val CHIP_COLOR = 0x73000000

        /** The grey of the blurred page at the top. */
        const val BLUR_TOP = 0xFF9C9C9C.toInt()

        /** The grey of the blurred page at the bottom. */
        const val BLUR_BOTTOM = 0xFF6B6B6B.toInt()

        /** The soft bands of the blurred page. */
        const val BLUR_BAND = 0x33FFFFFF
    }
}
