package com.peng.pdfreader.core.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Horizontal colour palette with a selection ring.
 *
 * The view paints the ten annotation colours of the design and reports the
 * picked one; it never decides what a colour means. The swatch size and the
 * gap are configurable because the palette is used in three different places
 * (the floating signature palette, the draw colour row and the highlight bar).
 */
class ColorPaletteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#22000000")
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#333333")
    }

    /** Diameter of a single swatch, in pixels. */
    var swatchDiameter: Float = dp(20f)
        set(value) {
            field = value.coerceAtLeast(1f)
            requestLayout()
        }

    /** Gap between two swatches, in pixels. */
    var itemSpacing: Float = dp(16f)
        set(value) {
            field = value.coerceAtLeast(0f)
            requestLayout()
        }

    /** Stroke width of the ring around the selected swatch, in pixels. */
    var ringWidth: Float = dp(2f)
        set(value) {
            field = value.coerceAtLeast(0f)
            requestLayout()
        }

    /** Whether the selection ring is painted. */
    var showSelectionRing: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    /** Whether the swatches get a hair line around them. */
    var showSwatchBorder: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Colour of the ring around the selected swatch. */
    var ringColor: Int = Color.parseColor("#333333")
        set(value) {
            field = value
            invalidate()
        }

    /** The swatches, in the order they are painted. */
    var colors: List<Int> = DEFAULT_COLORS
        set(value) {
            field = value.toList()
            if (selectedIndex > field.lastIndex) selectedIndex = field.lastIndex
            invalidate()
        }

    /** Index of the selected swatch. */
    var selectedIndex: Int = 0
        set(value) {
            val bounded = if (colors.isEmpty()) 0 else value.coerceIn(0, colors.lastIndex)
            if (field != bounded) {
                field = bounded
                invalidate()
            }
        }

    /** Colour of the selected swatch. */
    var selectedColor: Int
        get() = colors.getOrNull(selectedIndex) ?: Color.TRANSPARENT
        set(value) {
            val index = colors.indexOf(value)
            if (index >= 0) selectedIndex = index
        }

    /** Raised with the picked colour. */
    var onColorSelected: ((Int) -> Unit)? = null

    init {
        isClickable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val count = colors.size
        val contentWidth = if (count == 0) 0f else count * swatchDiameter + (count - 1) * itemSpacing
        val contentHeight = swatchDiameter + 2f * (dp(2f) + ringWidth)
        setMeasuredDimension(
            resolveSize((contentWidth + paddingLeft + paddingRight).toInt(), widthMeasureSpec),
            resolveSize((contentHeight + paddingTop + paddingBottom).toInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (colors.isEmpty()) return
        val radius = swatchDiameter / 2f
        val centreY = height / 2f
        val startX = paddingLeft + radius
        val step = swatchDiameter + spacing()
        colors.forEachIndexed { index, colour ->
            val centreX = startX + index * step
            if (showSelectionRing && index == selectedIndex) {
                ringPaint.color = ringColor
                ringPaint.strokeWidth = ringWidth
                // The ring of the design sits on the swatch: its inner edge
                // touches the colour and it is two pixels wide.
                canvas.drawCircle(centreX, centreY, radius + ringWidth / 2f, ringPaint)
            }
            fillPaint.color = colour
            canvas.drawCircle(centreX, centreY, radius, fillPaint)
            if (showSwatchBorder) {
                borderPaint.strokeWidth = dp(1f)
                canvas.drawCircle(centreX, centreY, radius, borderPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val index = indexAt(event.x)
                if (index >= 0) {
                    selectedIndex = index
                    onColorSelected?.invoke(colors[index])
                }
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    private fun indexAt(x: Float): Int {
        if (colors.isEmpty()) return -1
        val radius = swatchDiameter / 2f
        val startX = paddingLeft + radius
        val step = swatchDiameter + spacing()
        val raw = Math.round((x - startX) / step).toInt()
        if (raw !in colors.indices) return -1
        val centre = startX + raw * step
        return if (Math.abs(x - centre) <= radius + itemSpacing / 2f) raw else -1
    }

    /** The gap between two swatches. */
    private fun spacing(): Float = itemSpacing

    private fun dp(value: Float): Float = value * density

    companion object {
        /** The ten annotation colours of the design. */
        val DEFAULT_COLORS: List<Int> = listOf(
            Color.parseColor("#fb4141"),
            Color.parseColor("#fcb449"),
            Color.parseColor("#f9d925"),
            Color.parseColor("#42d053"),
            Color.parseColor("#2263e4"),
            Color.parseColor("#7333db"),
            Color.parseColor("#ff98df"),
            Color.parseColor("#644a4a"),
            Color.parseColor("#333333"),
            Color.parseColor("#ffffff"),
        )
    }
}
