package com.peng.pdfreader.feature.tools.view

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt

/**
 * The picture glyph of the design ("ic_image", 14x8, #9e9e9e).
 *
 * No asset exists for it, so the small landscape icon of the design is painted
 * here. The intrinsic size is the size of the design, the icon therefore sits
 * in the middle of the 24x24 action slot of the app bar.
 */
class ImageGlyphDrawable(
    density: Float,
    @ColorInt private val color: Int = 0xFF9E9E9E.toInt(),
) : Drawable() {

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        this.color = color
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

    private val width = (14 * density).toInt()
    private val height = (8 * density).toInt()

    override fun draw(canvas: Canvas) {
        val box = bounds
        val w = box.width().toFloat()
        val h = box.height().toFloat()
        val inset = stroke.strokeWidth / 2f
        canvas.drawRoundRect(
            RectF(inset, inset, w - inset, h - inset),
            stroke.strokeWidth,
            stroke.strokeWidth,
            stroke,
        )
        canvas.drawCircle(w * 0.26f, h * 0.3f, h * 0.1f, fill)
        val mountain = Path().apply {
            moveTo(w * 0.16f, h - inset)
            lineTo(w * 0.44f, h * 0.38f)
            lineTo(w * 0.64f, h - inset)
            close()
        }
        canvas.drawPath(mountain, fill)
    }

    override fun getIntrinsicWidth(): Int = width

    override fun getIntrinsicHeight(): Int = height

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
