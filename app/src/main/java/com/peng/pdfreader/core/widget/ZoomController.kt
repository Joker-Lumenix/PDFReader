package com.peng.pdfreader.core.widget

import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * The pinch zoom of a document preview.
 *
 * The pages of a PDF zoom themselves (the page view of the reader does that),
 * the previews of Word, Excel and PowerPoint use this controller: the content
 * of the reader is scaled, so the text of the document grows with it.
 */
class ZoomController(private val target: View) {

    /** The zoom of the content. */
    var zoom = 1f
        private set

    private val detector = ScaleGestureDetector(
        target.context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                apply((zoom * detector.scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM))
                return true
            }
        },
    )

    /**
     * Feeds the touch events of the reader into the detector.
     *
     * The events of the scroll view are needed - the detector only reacts to
     * a gesture with two fingers, the scroll of the reader stays as it is.
     */
    fun onTouch(event: MotionEvent): Boolean = detector.onTouchEvent(event)

    /** Sets the zoom of the content. */
    fun apply(value: Float) {
        zoom = value
        target.scaleX = value
        target.scaleY = value
    }

    /** The zoom of the content, the reader starts at the normal size. */
    fun reset() = apply(1f)

    companion object {
        /** The content is never smaller than it is. */
        const val MIN_ZOOM = 1f

        /** The content is never bigger than four times. */
        const val MAX_ZOOM = 4f
    }
}
