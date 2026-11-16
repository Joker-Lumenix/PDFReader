package com.peng.pdfreader.feature.files.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue

/**
 * The file type logo with the page count chip of the design drawn on top.
 *
 * The design shows a small dark rounded rectangle with the number of pages in
 * the lower right corner of the thumbnail; there is no drawable for it in the
 * asset set, so it is painted here.
 */
class PageCountDrawable(
    base: Drawable,
    private val pageCount: Int,
    context: Context,
) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val textSizePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        TEXT_SP,
        context.resources.displayMetrics,
    )
    private val bitmap: Bitmap = render(base)

    private fun render(base: Drawable): Bitmap {
        val size = (THUMBNAIL_DP * density).toInt().coerceAtLeast(1)
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        base.setBounds(0, 0, size, size)
        base.draw(canvas)

        if (pageCount <= 0) return result

        val label = if (pageCount > MAX_PAGES) "$MAX_PAGES+" else pageCount.toString()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = textSizePx
            typeface = Typeface.DEFAULT_BOLD
        }
        val metrics = textPaint.fontMetrics
        val textWidth = textPaint.measureText(label)
        val textHeight = metrics.descent - metrics.ascent
        val paddingH = dp(8f)
        val paddingV = dp(1f)
        val chipWidth = textWidth + paddingH * 2
        val chipHeight = textHeight + paddingV * 2
        val inset = dp(2f)
        val left = size - chipWidth - inset
        val top = size - chipHeight - inset

        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(115, 0, 0, 0)
        }
        canvas.drawRoundRect(left, top, left + chipWidth, top + chipHeight, dp(4f), dp(4f), chipPaint)
        canvas.drawText(label, left + paddingH, top + paddingV - metrics.ascent, textPaint)
        return result
    }

    private fun dp(value: Float): Float = value * density

    override fun draw(canvas: Canvas) {
        val target = bounds
        if (target.isEmpty) return
        canvas.drawBitmap(bitmap, null, target, null)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = bitmap.width

    override fun getIntrinsicHeight(): Int = bitmap.height

    companion object {
        private const val THUMBNAIL_DP = 48f
        private const val TEXT_SP = 10f
        private const val MAX_PAGES = 99
    }
}
