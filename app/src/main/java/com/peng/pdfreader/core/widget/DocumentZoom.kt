package com.peng.pdfreader.core.widget

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import kotlin.math.abs

/**
 * The pinch zoom of the previews of Word and PowerPoint.
 *
 * The content of the reader is scaled with two fingers and moved with one
 * finger, as long as the content can be moved further. A gesture that the
 * content cannot take (a drag that would move the content out of the reader)
 * is left to the reader, so the document can still be scrolled and the slides
 * can still be turned.
 *
 * The PDF reader zooms the pages on their own (the page view of the reader
 * does that), this controller is for the previews of the office documents.
 */
internal class DocumentZoom(private val host: ViewGroup) {

    /** The zoom of the content. */
    var zoom = 1f
        private set

    /** The move of the content in the x direction. */
    var offsetX = 0f
        private set

    /** The move of the content in the y direction. */
    var offsetY = 0f
        private set

    /** The width of the content that is zoomed. */
    var contentWidth = 0f
        private set

    /** The height of the content that is zoomed. */
    var contentHeight = 0f
        private set

    /** Whether a zoom gesture is running. */
    private var scaling = false

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(
        host.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                scaling = true
                // The content of the reader must not scroll while it is zoomed.
                group()?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomBy(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                scaling = false
            }
        },
    )

    /**
     * The gesture that zooms the content with a double tap.
     *
     * The detector only watches the events, the reader keeps its own handling
     * of them.
     */
    private val doubleTapDetector = GestureDetector(
        host.context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(event: MotionEvent): Boolean {
                // A double tap zooms the content in and out again.
                val target = if (zoom > 1f + ZOOM_EPSILON) 1f else DOUBLE_TAP_ZOOM
                zoomBy(target / zoom, event.x, event.y)
                return true
            }
        },
    )

    /** The content that is zoomed. */
    private fun content(): View? = host.getChildAt(0)

    /**
     * The content of the reader.
     *
     * The reader of the document is a group, it has to be told that it must
     * not scroll while the content is zoomed.
     */
    private fun group(): ViewGroup? = content() as? ViewGroup

    /** The size of the content, it is told when the content is measured. */
    fun setContentSize(width: Float, height: Float) {
        contentWidth = width
        contentHeight = height
    }

    /**
     * The events of the reader.
     *
     * Every event of the gesture is seen here, so the zoom knows the whole
     * gesture and not only its end.
     */
    fun onTouchEvent(event: MotionEvent) {
        scaleDetector.onTouchEvent(event)
        doubleTapDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Two fingers mean a zoom, the reader must not scroll.
                scaling = true
                group()?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                if (zoom > 1f && !scaling) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (abs(dx) > TOUCH_SLOP || abs(dy) > TOUCH_SLOP) {
                        // The content is moved, the reader must not scroll.
                        group()?.requestDisallowInterceptTouchEvent(true)
                        offsetX += dx
                        offsetY += dy
                        clamp()
                        apply()
                    }
                }
                lastX = event.x
                lastY = event.y
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                scaling = false
                group()?.requestDisallowInterceptTouchEvent(false)
            }
        }
    }

    /** The zoom is put back to the normal size. */
    fun reset() {
        zoom = 1f
        offsetX = 0f
        offsetY = 0f
        clamp()
        apply()
    }

    /**
     * Puts the zoom on the content.
     *
     * The content is scaled around its top left corner, so the corner stays
     * where it is and the content is moved around it.
     */
    fun applyTo(target: View) {
        target.pivotX = 0f
        target.pivotY = 0f
        target.scaleX = zoom
        target.scaleY = zoom
        target.translationX = offsetX
        target.translationY = offsetY
    }

    /**
     * The zoom of the content is changed around the point under the fingers.
     *
     * The gesture of the reader calls it, the test of the app calls it too.
     */
    internal fun zoomBy(factor: Float, focusX: Float, focusY: Float) {
        val previous = zoom
        val next = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (next == previous) return
        zoom = next
        // The point under the fingers stays under the fingers.
        offsetX = focusX - (focusX - offsetX) * next / previous
        offsetY = focusY - (focusY - offsetY) * next / previous
        clamp()
        apply()
    }

    /** The change of the zoom is given to the content. */
    private fun apply() {
        val child = content() ?: return
        applyTo(child)
    }

    /** The content stays in the reader. */
    private fun clamp() {
        offsetX = offsetX.coerceIn(minOffsetX(), maxOffsetX())
        offsetY = offsetY.coerceIn(minOffsetY(), maxOffsetY())
    }

    private fun minOffsetX(): Float = host.width.toFloat() - contentWidth * zoom

    private fun maxOffsetX(): Float = 0f

    private fun minOffsetY(): Float = host.height.toFloat() - contentHeight * zoom

    private fun maxOffsetY(): Float = 0f

    companion object {
        /** The content is never smaller than it is. */
        const val MIN_ZOOM = 1f

        /** The content is never bigger than five times. */
        const val MAX_ZOOM = 5f

        /** The distance a finger has to walk before the reader reacts. */
        const val TOUCH_SLOP = 12f

        /**
         * The zoom of a double tap.
         *
         * A second tap on the content brings it back to the normal size.
         */
        const val DOUBLE_TAP_ZOOM = 2.5f

        /** The difference below which the zoom counts as the normal size. */
        const val ZOOM_EPSILON = 0.01f
    }
}
