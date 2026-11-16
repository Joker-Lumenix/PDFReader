package com.peng.pdfreader.feature.language

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.peng.pdfreader.R

/**
 * The radio button of a language row.
 *
 * The design draws an 18dp circle: an empty ring in light grey while the row
 * is not selected and a filled circle with a check mark on the selected row -
 * white with a brand red check, because the selected row itself is the red
 * gradient pill.
 */
class LanguageRadioDrawable(
    private val selected: Boolean,
    private val density: Float,
    brandColor: Int,
    ringColor: Int,
) : Drawable() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = brandColor
        strokeWidth = 1.8f * density
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ringColor
        strokeWidth = density
    }

    private val check = Path()

    override fun draw(canvas: Canvas) {
        val radius = minOf(bounds.width(), bounds.height()) / 2f
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()

        if (selected) {
            canvas.drawCircle(centerX, centerY, radius, fillPaint)
            check.reset()
            check.moveTo(centerX - radius * 0.42f, centerY + radius * 0.02f)
            check.lineTo(centerX - radius * 0.10f, centerY + radius * 0.34f)
            check.lineTo(centerX + radius * 0.44f, centerY - radius * 0.30f)
            canvas.drawPath(check, strokePaint)
        } else {
            canvas.drawCircle(centerX, centerY, radius - 0.5f * density, ringPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        ringPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        ringPaint.colorFilter = colorFilter
    }

    @Deprecated("Kept for the platform contract of Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        fun of(context: Context, selected: Boolean): LanguageRadioDrawable =
            LanguageRadioDrawable(
                selected = selected,
                density = context.resources.displayMetrics.density,
                brandColor = ContextCompat.getColor(context, R.color.brand_primary),
                ringColor = ContextCompat.getColor(context, R.color.surface_border_strong),
            )
    }
}
