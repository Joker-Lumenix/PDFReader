package com.peng.pdfreader.appcore.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * The check mark of the confirm pill.
 *
 * The design draws a white 15 x 10 mark inside the 55 x 24 pill of the two
 * editors; the view scales that mark to whatever size it is given.
 */
class CheckMarkView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }

    /** Colour of the mark. */
    var markColor: Int = Color.WHITE
        set(value) {
            field = value
            markPaint.color = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val scale = min(w / MARK_WIDTH, h / MARK_HEIGHT)
        val left = (w - MARK_WIDTH * scale) / 2f
        val top = (h - MARK_HEIGHT * scale) / 2f
        markPaint.strokeWidth = STROKE_WIDTH * scale
        val path = Path().apply {
            moveTo(left + 1.4f * scale, top + 5.2f * scale)
            lineTo(left + 5.2f * scale, top + 8.8f * scale)
            lineTo(left + 13.6f * scale, top + 1.2f * scale)
        }
        canvas.drawPath(path, markPaint)
    }

    companion object {
        private const val MARK_WIDTH = 15f
        private const val MARK_HEIGHT = 10f
        private const val STROKE_WIDTH = 2f
    }
}
