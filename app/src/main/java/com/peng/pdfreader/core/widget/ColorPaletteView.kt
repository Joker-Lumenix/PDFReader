package com.peng.pdfreader.core.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Horizontal colour palette with a selection ring.
 *
 * The view paints the annotation colours of the design and reports the picked
 * one; it never decides what a colour means. The swatch size and the gap are
 * configurable because the palette is used in three different places (the
 * floating signature palette, the draw colour row and the highlight bar).
 *
 * The strip can hold any number of colours: when they do not fit into the
 * space of the view, a drag scrolls them and a tap picks one.
 */
class ColorPaletteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val scroller = OverScroller(context)

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
    var ringWidth: Float = dp(1f)
        set(value) {
            field = value.coerceAtLeast(0f)
            requestLayout()
        }

    /**
     * The gap between the swatch and the ring around it.
     *
     * The ring of the design does not touch the colour, a thin gap is left.
     */
    var ringGap: Float = dp(1f)
        set(value) {
            field = value.coerceAtLeast(0f)
            invalidate()
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
            scrollTo(scrollOffset)
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

    /** How far the colours are scrolled, in pixels. */
    private var scrollOffset = 0f

    /** Where the finger went down, in view pixels. */
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var dragging = false
    private var velocityTracker: VelocityTracker? = null

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
        val step = swatchDiameter + itemSpacing
        // The colours that do not fit into the view are scrolled.
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        canvas.translate(-scrollOffset, 0f)
        colors.forEachIndexed { index, colour ->
            val centreX = startX + index * step
            if (showSelectionRing && index == selectedIndex) {
                ringPaint.color = ringColor
                ringPaint.strokeWidth = ringWidth
                // The ring of the design is a thin circle with a gap between
                // it and the colour: 28dp around a 24dp swatch.
                canvas.drawCircle(
                    centreX,
                    centreY,
                    radius + ringGap + ringWidth / 2f,
                    ringPaint,
                )
            }
            fillPaint.color = colour
            canvas.drawCircle(centreX, centreY, radius, fillPaint)
            if (showSwatchBorder) {
                borderPaint.strokeWidth = dp(1f)
                canvas.drawCircle(centreX, centreY, radius, borderPaint)
            }
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                dragging = false
                scroller.forceFinished(true)
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.x - lastX
                if (!dragging &&
                    abs(event.x - downX) > touchSlop &&
                    abs(event.x - downX) > abs(event.y - downY)
                ) {
                    // The strip owns the gesture, the page behind it must not
                    // take it over.
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) {
                    scrollBy(-dx)
                    lastX = event.x
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                if (dragging) {
                    fling()
                } else {
                    pick(event.x)
                }
                releaseTracker()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                releaseTracker()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollOffset = scroller.currX.toFloat()
            invalidate()
            postInvalidateOnAnimation()
        }
    }

    override fun performClick(): Boolean = super.performClick()

    /** Picks the swatch under the finger. */
    private fun pick(x: Float) {
        val index = indexAt(x + scrollOffset)
        if (index >= 0) {
            selectedIndex = index
            onColorSelected?.invoke(colors[index])
        }
        performClick()
    }

    /** Scrolls the strip by the given distance. */
    private fun scrollBy(distance: Float) {
        scrollTo(scrollOffset + distance)
    }

    /** Moves the strip to the given place. */
    private fun scrollTo(offset: Float) {
        val clamped = offset.coerceIn(0f, maxScroll())
        if (clamped != scrollOffset) {
            scrollOffset = clamped
            invalidate()
        }
    }

    /** How far the strip can be scrolled. */
    private fun maxScroll(): Float = max(0f, contentWidth() - width)

    /** The width of all the swatches together. */
    private fun contentWidth(): Float {
        val count = colors.size
        if (count == 0) return 0f
        return count * swatchDiameter + (count - 1) * itemSpacing
    }

    /** Lets the strip glide on after the finger left it. */
    private fun fling() {
        val max = maxScroll()
        if (max <= 0f) return
        val tracker = velocityTracker ?: return
        tracker.computeCurrentVelocity(1000)
        val velocity = tracker.xVelocity
        scroller.forceFinished(true)
        scroller.fling(
            scrollOffset.toInt(),
            0,
            -velocity.toInt(),
            0,
            0,
            max.toInt(),
            0,
            0,
        )
        postInvalidateOnAnimation()
    }

    private fun releaseTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun indexAt(x: Float): Int {
        if (colors.isEmpty()) return -1
        val radius = swatchDiameter / 2f
        val startX = paddingLeft + radius
        val step = swatchDiameter + itemSpacing
        val raw = Math.round((x - startX) / step).toInt()
        if (raw !in colors.indices) return -1
        val centre = startX + raw * step
        return if (Math.abs(x - centre) <= radius + itemSpacing / 2f) raw else -1
    }

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
