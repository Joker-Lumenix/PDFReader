package com.peng.pdfreader.core.widget

import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pinch zoom of the previews.
 *
 * The gesture of two fingers cannot be built by hand: the platform only
 * accepts the gesture of the touch screen. The zoom itself is checked here,
 * the gesture of the reader calls it.
 */
@RunWith(AndroidJUnit4::class)
class DocumentZoomTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theZoomScalesTheContent() {
        var zoom = 0f
        var scale = 0f
        onMain {
            val host = FrameLayout(context)
            val child = View(context)
            host.addView(child, FrameLayout.LayoutParams(200, 200))
            host.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            )
            host.layout(0, 0, 400, 400)

            val controller = DocumentZoom(host)
            controller.setContentSize(400f, 400f)

            controller.zoomBy(2f, 200f, 200f)

            zoom = controller.zoom
            scale = child.scaleX
        }

        assertTrue("the content is not zoomed: $zoom", zoom > 1f)
        assertTrue("the content is not scaled: $scale", scale > 1f)
    }

    @Test
    fun theZoomKeepsTheContentInTheReader() {
        var offset = 0f
        onMain {
            val host = FrameLayout(context)
            val child = View(context)
            host.addView(child, FrameLayout.LayoutParams(200, 200))
            host.measure(
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            )
            host.layout(0, 0, 400, 400)

            val controller = DocumentZoom(host)
            controller.setContentSize(400f, 400f)

            // A zoom that is far out of the reader is put back to the size of
            // the content, the content never leaves the reader.
            controller.zoomBy(40f, 200f, 200f)
            controller.zoomBy(0.01f, 200f, 200f)
            controller.zoomBy(0.01f, 200f, 200f)

            offset = controller.offsetX
        }

        assertTrue("the content is not inside the reader: $offset", offset >= 0f)
    }

    /** Runs the block on the main thread of the app. */
    private fun onMain(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }
}
