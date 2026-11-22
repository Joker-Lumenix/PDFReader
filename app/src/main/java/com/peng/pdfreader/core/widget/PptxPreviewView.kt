package com.peng.pdfreader.core.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.peng.pdfreader.R
import com.peng.pdfreader.core.office.OfficeColor
import com.peng.pdfreader.core.office.OfficeColors
import com.peng.pdfreader.core.office.pptx.PptxDeck
import com.peng.pdfreader.core.office.pptx.PptxGradient
import com.peng.pdfreader.core.office.pptx.PptxParagraph
import com.peng.pdfreader.core.office.pptx.PptxRun
import com.peng.pdfreader.core.office.pptx.PptxShape
import com.peng.pdfreader.core.office.pptx.PptxSlide
import com.peng.pdfreader.core.office.pptx.PptxTableStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.min

/**
 * Renders a parsed presentation, one slide at a time.
 *
 * The view hosts a `ViewPager2`; every page of the pager is a [PptxSlideView]
 * that paints a single slide.
 */
class PptxPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val pager = ViewPager2(context)
    private val adapter = SlideAdapter()

    /** The pinch zoom of the slides. */
    private val zoom = DocumentZoom(this)

    /** The slides that are read so far. */
    private val slides = ArrayList<PptxSlide?>()

    /** The width of the deck in deck dp. */
    private var deckWidthDp = 0f

    /** The height of the deck in deck dp. */
    private var deckHeightDp = 0f

    /** The number of the slides of the deck. */
    var slideCount: Int = 0
        set(value) {
            field = value
            while (slides.size < value) slides.add(null)
            adapter.notifyDataSetChanged()
        }

    /** The size of the deck, the slides are scaled into it. */
    fun setDeckSize(widthDp: Float, heightDp: Float) {
        deckWidthDp = widthDp
        deckHeightDp = heightDp
        invalidate()
    }

    /**
     * Adds a slide to the deck.
     *
     * The slides are read one after the other, the deck grows while it is
     * read, so the first slide is on the screen right away.
     */
    fun appendSlide(slide: PptxSlide) {
        val index = slides.indexOfFirst { it == null }
        if (index < 0) {
            slides.add(slide)
            slideCount = slides.size
            adapter.notifyItemInserted(slides.size - 1)
            return
        }
        slides[index] = slide
        adapter.notifyItemChanged(index)
    }

    /** The deck that is rendered. */
    var deck: PptxDeck? = null
        set(value) {
            field = value
            slides.clear()
            value?.slides?.forEach { slides.add(it) }
            if (value != null) {
                deckWidthDp = value.widthDp
                deckHeightDp = value.heightDp
                slideCount = slides.size
            }
            adapter.notifyDataSetChanged()
            if (value != null && value.slides.isNotEmpty()) {
                pager.setCurrentItem(0, false)
            }
        }

    /**
     * Shows the slide with the given number.
     *
     * The reader uses it to go back to the slide the user was on when the
     * document was read again after a change.
     */
    fun scrollToSlide(slide: Int) {
        val index = slide.coerceIn(0, max(slideCount - 1, 0))
        pager.setCurrentItem(index, false)
    }

    /** Raised with the index of the slide the pager shows. */
    var onSlideChanged: ((Int) -> Unit)? = null

    /**
     * Raised with the text box the user holds a finger on.
     *
     * The editor of the app opens the text of the box, so the reader only has
     * to tell which box of the slide was touched.
     */
    var onShapeLongPressed: ((PptxShape.TextBox) -> Unit)? = null

    /**
     * The gesture of the reader.
     *
     * The detector only watches the events, the pager keeps its own handling
     * of them.
     */
    private val longPressDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(event: MotionEvent) {
                val slide = slideAt(event.x, event.y) ?: return
                val shape = slide.shapeAt(event.x - slide.left, event.y - slide.top) ?: return
                onShapeLongPressed?.invoke(shape)
            }
        },
    )

    /** Index of the slide the pager shows. */
    val currentSlide: Int get() = pager.currentItem

    init {
        addView(
            pager,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        pager.adapter = adapter
        // The zoomed slides may be bigger than the reader, the reader of the
        // app clips them at the border of the preview.
        clipChildren = false
        pager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    onSlideChanged?.invoke(position)
                }
            },
        )
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // The zoom sees every event of the gesture, the reader keeps its own.
        zoom.onTouchEvent(event)
        // The gesture that opens the editor is watched here, the pager of the
        // slides would swallow it.
        longPressDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    /**
     * The slide at the given point of the reader.
     *
     * The pager holds the slides in a reader of its own, it fills the pager
     * completely, so the point of the reader is the point of the reader of
     * the slides.
     */
    private fun slideAt(x: Float, y: Float): PptxSlideView? {
        val list = recyclerView() ?: return null
        return list.findChildViewUnder(x, y) as? PptxSlideView
    }

    /** The reader of the slides of the pager. */
    private fun recyclerView(): RecyclerView? {
        var found: RecyclerView? = null
        fun walk(group: ViewGroup) {
            for (index in 0 until group.childCount) {
                val child = group.getChildAt(index)
                if (child is RecyclerView) {
                    found = child
                    return
                }
                if (child is ViewGroup) walk(child)
            }
        }
        walk(pager)
        return found
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        zoom.setContentSize(w.toFloat(), h.toFloat())
    }

    /** Shows the slide with the given index. */
    fun setSlide(index: Int) {
        if (index in 0 until adapter.itemCount) pager.currentItem = index
    }

    private inner class SlideAdapter : RecyclerView.Adapter<SlideHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideHolder {
            val view = PptxSlideView(parent.context)
            // ViewPager2 only accepts pages that fill it completely.
            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            return SlideHolder(view)
        }

        override fun onBindViewHolder(holder: SlideHolder, position: Int) {
            holder.view.bind(slides.getOrNull(position), deckWidthDp, deckHeightDp)
        }

        override fun getItemCount(): Int = slides.size
    }

    private class SlideHolder(val view: PptxSlideView) : RecyclerView.ViewHolder(view)
}

/**
 * Paints a single slide of a presentation.
 */
class PptxSlideView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var slide: PptxSlide? = null
    private var deckWidthDp = 0f
    private var deckHeightDp = 0f

    /** The bitmaps of the pictures of the slide, every picture is decoded once. */
    private val bitmaps = HashMap<PptxShape.Picture, Bitmap>()

    /** The bitmap of the picture that fills the background of the slide. */
    private var backgroundBitmap: Bitmap? = null

    /** Binds a slide to the view. */
    fun bind(slide: PptxSlide?, deckWidthDp: Float, deckHeightDp: Float) {
        this.slide = slide
        this.deckWidthDp = deckWidthDp
        this.deckHeightDp = deckHeightDp
        bitmaps.clear()
        backgroundBitmap = null
        invalidate()
    }

    /**
     * The shape at the given point of the slide.
     *
     * The reader asks for it when the user holds a finger on the slide, so it
     * knows which text box of the slide is going to be edited.
     *
     * @return the shape, null when the point is not on a text box
     */
    fun shapeAt(x: Float, y: Float): PptxShape.TextBox? {
        val current = slide ?: return null
        if (width == 0 || height == 0) return null
        if (deckWidthDp <= 0f || deckHeightDp <= 0f) return null
        val scale = scale()
        val left = (width - deckWidthDp * scale) / 2f
        val top = (height - deckHeightDp * scale) / 2f
        // The point in the space of the slide, without the scale.
        val contentX = (x - left) / scale
        val contentY = (y - top) / scale
        // The shapes are drawn one after the other, the last one is on the
        // top of the others.
        for (shape in current.shapes.reversed()) {
            if (shape !is PptxShape.TextBox) continue
            val rect = shape.rect(1f)
            if (contentX < rect.left || contentX > rect.right) continue
            if (contentY < rect.top || contentY > rect.bottom) continue
            return shape
        }
        return null
    }

    /** The factor the slide is scaled by to fit into the view. */
    private fun scale(): Float = min(
        width / deckWidthDp.coerceAtLeast(1f),
        height / deckHeightDp.coerceAtLeast(1f),
    )

    override fun onDraw(canvas: Canvas) {
        val current = slide ?: return
        if (width == 0 || height == 0) return
        if (deckWidthDp <= 0f || deckHeightDp <= 0f) return

        val scale = scale()
        val left = (width - deckWidthDp * scale) / 2f
        val top = (height - deckHeightDp * scale) / 2f
        val slideRect = RectF(
            left,
            top,
            left + deckWidthDp * scale,
            top + deckHeightDp * scale,
        )

        current.background?.let {
            fillPaint.color = OfficeColors.toAndroid(it)
            canvas.drawRect(slideRect, fillPaint)
        }
        // A slide can have a picture as its background, it lies behind the
        // shapes.
        current.backgroundImage?.let { bytes ->
            val bitmap = backgroundBitmap ?: decode(bytes)?.also { backgroundBitmap = it }
            bitmap?.let { canvas.drawBitmap(it, null, slideRect, bitmapPaint) }
        }

        // The shapes live in the coordinate space of the slide, so the canvas
        // is moved to the top left corner of the slide before they are drawn.
        canvas.save()
        canvas.translate(left, top)
        for (shape in current.shapes) {
            when (shape) {
                is PptxShape.Rectangle -> drawRectangle(canvas, shape, scale)
                is PptxShape.Picture -> drawPicture(canvas, shape, scale)
                is PptxShape.TextBox -> drawTextBox(canvas, shape, scale)
                is PptxShape.Table -> drawTable(canvas, shape, scale)
                is PptxShape.Chart -> PptxChartRenderer.draw(canvas, shape.chart, shape.rect(scale), scale)
            }
        }
        canvas.restore()
    }

    private fun drawRectangle(canvas: Canvas, shape: PptxShape.Rectangle, scale: Float) {
        val rect = shape.rect(scale)
        val rotation = shape.rotation
        if (rotation != 0f) {
            // The shape is turned around its middle, the way PowerPoint does.
            canvas.save()
            canvas.rotate(rotation, rect.centerX(), rect.centerY())
        }
        val gradient = shape.gradient
        val shader = if (gradient != null) shaderOf(gradient, rect) else null
        if (shader != null) {
            fillPaint.shader = shader
            drawShape(canvas, shape.preset, rect, fillPaint)
            fillPaint.shader = null
        } else {
            shape.fill?.let {
                fillPaint.color = OfficeColors.toAndroid(it)
                drawShape(canvas, shape.preset, rect, fillPaint)
            }
        }
        shape.line?.let {
            linePaint.color = OfficeColors.toAndroid(it)
            linePaint.strokeWidth = max(1f, shape.lineWidthDp * scale)
            drawShape(canvas, shape.preset, rect, linePaint)
        }
        if (rotation != 0f) canvas.restore()
    }

    /**
     * Draws a shape with the geometry of the presentation.
     *
     * The shapes that the reader does not know are drawn as a rectangle.
     */
    private fun drawShape(canvas: Canvas, preset: String, rect: RectF, paint: Paint) {
        when (preset) {
            "roundRect", "round2SameRect", "round1Rect", "flowChartTerminator",
            "wedgeRoundRectCallout", "wedgeRoundRectCallout",
            -> canvas.drawRoundRect(rect, radiusOf(rect), radiusOf(rect), paint)

            "ellipse", "flowChartConnector", "circle", "flowChartAlternateProcess",
            -> canvas.drawOval(rect, paint)

            "triangle" -> canvas.drawPath(triangleOf(rect), paint)
            "rtTriangle" -> canvas.drawPath(rightTriangleOf(rect), paint)
            "diamond", "flowChartDecision" -> canvas.drawPath(diamondOf(rect), paint)
            "line", "straightConnector1" -> canvas.drawLine(rect.left, rect.top, rect.right, rect.bottom, paint)

            // The polygons.
            "pentagon", "flowChartOffpageConnector" -> canvas.drawPath(polygonOf(rect, 5), paint)
            "heptagon" -> canvas.drawPath(polygonOf(rect, 7), paint)
            "hexagon" -> canvas.drawPath(polygonOf(rect, 6), paint)
            "octagon" -> canvas.drawPath(polygonOf(rect, 8), paint)
            "decagon" -> canvas.drawPath(polygonOf(rect, 10), paint)

            // The arrows.
            "rightArrow" -> canvas.drawPath(arrowOf(rect, Arrow.RIGHT), paint)
            "leftArrow" -> canvas.drawPath(arrowOf(rect, Arrow.LEFT), paint)
            "upArrow" -> canvas.drawPath(arrowOf(rect, Arrow.UP), paint)
            "downArrow" -> canvas.drawPath(arrowOf(rect, Arrow.DOWN), paint)
            "leftRightArrow" -> canvas.drawPath(arrowOf(rect, Arrow.BOTH), paint)
            "chevron" -> canvas.drawPath(chevronOf(rect), paint)
            "homePlate" -> canvas.drawPath(homePlateOf(rect), paint)

            // The shapes of a table of a flow chart.
            "trapezoid" -> canvas.drawPath(trapezoidOf(rect), paint)
            "flowChartManualInput" -> canvas.drawPath(trapezoidOf(rect), paint)
            "flowChartManualOperation" -> canvas.drawPath(trapezoidOf(rect, flip = true), paint)
            "parallelogram", "flowChartData" -> canvas.drawPath(parallelogramOf(rect), paint)
            "flowChartProcess", "flowChartPredefinedProcess", "flowChartDocument",
            "snip1Rect", "snip2SameRect", "plaque", "frame", "halfFrame",
            -> canvas.drawRect(rect, paint)

            // The stars.
            "star4" -> canvas.drawPath(starOf(rect, 4), paint)
            "star5" -> canvas.drawPath(starOf(rect, 5), paint)
            "star6" -> canvas.drawPath(starOf(rect, 6), paint)
            "star8" -> canvas.drawPath(starOf(rect, 8), paint)

            // The shapes with a hole or a cut.
            "donut" -> canvas.drawPath(donutOf(rect), paint)
            "pie" -> canvas.drawPath(pieOf(rect), paint)
            "plus" -> canvas.drawPath(plusOf(rect), paint)
            "heart" -> canvas.drawPath(heartOf(rect), paint)
            "lightningBolt" -> canvas.drawPath(lightningOf(rect), paint)
            "cloud" -> canvas.drawPath(cloudOf(rect), paint)
            "teardrop" -> canvas.drawPath(teardropOf(rect), paint)
            "moon" -> canvas.drawPath(moonOf(rect), paint)

            // The three dimensional shapes are drawn as their front.
            "can", "cube", "bevel" -> canvas.drawRect(rect, paint)

            else -> canvas.drawRect(rect, paint)
        }
    }

    /** The direction of an arrow. */
    private enum class Arrow { RIGHT, LEFT, UP, DOWN, BOTH }

    /**
     * The shader of a gradient fill.
     *
     * The direction of the gradient is the one of the presentation, a
     * horizontal gradient goes from the left to the right.
     */
    private fun shaderOf(gradient: PptxGradient, rect: RectF): Shader? {
        val stops = gradient.stops
        if (stops.size < 2) return null
        val angle = Math.toRadians(gradient.angleDeg.toDouble())
        val radius = max(rect.width(), rect.height())
        val dx = cos(angle).toFloat() * radius / 2f
        val dy = sin(angle).toFloat() * radius / 2f
        val colors = IntArray(stops.size) { OfficeColors.toAndroid(stops[it].color) }
        val positions = FloatArray(stops.size) { stops[it].position }
        return LinearGradient(
            rect.centerX() - dx,
            rect.centerY() - dy,
            rect.centerX() + dx,
            rect.centerY() + dy,
            colors,
            positions,
            Shader.TileMode.CLAMP,
        )
    }

    /** The radius of a rounded shape. */
    private fun radiusOf(rect: RectF): Float = min(rect.width(), rect.height()) * 0.16f

    /** The triangle of a shape. */
    private fun triangleOf(rect: RectF): Path = Path().apply {
        moveTo(rect.centerX(), rect.top)
        lineTo(rect.right, rect.bottom)
        lineTo(rect.left, rect.bottom)
        close()
    }

    /** The triangle of a shape that hangs on the right. */
    private fun rightTriangleOf(rect: RectF): Path = Path().apply {
        moveTo(rect.left, rect.top)
        lineTo(rect.right, rect.bottom)
        lineTo(rect.left, rect.bottom)
        close()
    }

    /** The diamond of a shape. */
    private fun diamondOf(rect: RectF): Path = Path().apply {
        moveTo(rect.centerX(), rect.top)
        lineTo(rect.right, rect.centerY())
        lineTo(rect.centerX(), rect.bottom)
        lineTo(rect.left, rect.centerY())
        close()
    }

    /**
     * A polygon with the given number of corners.
     *
     * The first corner is at the top, the shape turns clockwise.
     */
    private fun polygonOf(rect: RectF, corners: Int): Path {
        val path = Path()
        val radius = min(rect.width(), rect.height()) / 2f
        val centreX = rect.centerX()
        val centreY = rect.centerY()
        for (index in 0 until corners) {
            // The first corner is straight up, the others follow clockwise.
            val angle = -HALF_TURN + index * (FULL_TURN / corners)
            val x = centreX + cos(angle) * radius
            val y = centreY + sin(angle) * radius
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /** The arrow that points into a direction. */
    private fun arrowOf(rect: RectF, direction: Arrow): Path = Path().apply {
        val head = min(rect.width(), rect.height()) * 0.4f
        when (direction) {
            Arrow.RIGHT, Arrow.LEFT -> {
                val tip = if (direction == Arrow.RIGHT) rect.right else rect.left
                val base = if (direction == Arrow.RIGHT) rect.left else rect.right
                val middle = (rect.top + rect.bottom) / 2f
                moveTo(base, rect.top)
                lineTo(base + (tip - base) - head * sign(tip - base), rect.top)
                lineTo(base + (tip - base) - head * sign(tip - base), rect.top + head / 2f)
                lineTo(tip, middle)
                lineTo(base + (tip - base) - head * sign(tip - base), rect.bottom - head / 2f)
                lineTo(base + (tip - base) - head * sign(tip - base), rect.bottom)
                lineTo(base, rect.bottom)
            }
            Arrow.UP, Arrow.DOWN -> {
                val tip = if (direction == Arrow.UP) rect.top else rect.bottom
                val base = if (direction == Arrow.UP) rect.bottom else rect.top
                val middle = (rect.left + rect.right) / 2f
                moveTo(rect.left, base)
                lineTo(rect.left + head / 2f, base)
                lineTo(middle, tip)
                lineTo(rect.right - head / 2f, base)
                lineTo(rect.right, base)
            }
            Arrow.BOTH -> {
                val head = min(rect.width(), rect.height()) * 0.4f
                val middle = (rect.top + rect.bottom) / 2f
                moveTo(rect.left, rect.top)
                lineTo(rect.left + head, rect.top)
                lineTo(rect.left + head, middle - head / 2f)
                lineTo(rect.right - head, middle - head / 2f)
                lineTo(rect.right - head, rect.top)
                lineTo(rect.right, rect.top)
                lineTo(rect.right - head / 2f, middle)
                lineTo(rect.right, rect.bottom)
                lineTo(rect.right - head, rect.bottom)
                lineTo(rect.right - head, middle + head / 2f)
                lineTo(rect.left + head, middle + head / 2f)
                lineTo(rect.left + head, rect.bottom)
                lineTo(rect.left, rect.bottom)
                lineTo(rect.left + head / 2f, middle)
            }
        }
        close()
    }

    /** The shape of a chevron, the arrow with a notch in it. */
    private fun chevronOf(rect: RectF): Path = Path().apply {
        val notch = rect.width() * 0.25f
        moveTo(rect.left, rect.top)
        lineTo(rect.right - notch, rect.top)
        lineTo(rect.right, (rect.top + rect.bottom) / 2f)
        lineTo(rect.right - notch, rect.bottom)
        lineTo(rect.left, rect.bottom)
        lineTo(rect.left + notch, (rect.top + rect.bottom) / 2f)
        close()
    }

    /** The shape of a home plate, the rectangle with a point on the right. */
    private fun homePlateOf(rect: RectF): Path = Path().apply {
        val point = rect.width() * 0.25f
        moveTo(rect.left, rect.top)
        lineTo(rect.right - point, rect.top)
        lineTo(rect.right, (rect.top + rect.bottom) / 2f)
        lineTo(rect.right - point, rect.bottom)
        lineTo(rect.left, rect.bottom)
        close()
    }

    /** The shape of a trapezoid, the sides lean into the same direction. */
    private fun trapezoidOf(rect: RectF, flip: Boolean = false): Path = Path().apply {
        val lean = rect.width() * 0.2f
        val top = if (flip) rect.bottom else rect.top
        val bottom = if (flip) rect.top else rect.bottom
        moveTo(rect.left + lean, top)
        lineTo(rect.right - lean, top)
        lineTo(rect.right, bottom)
        lineTo(rect.left, bottom)
        close()
    }

    /** The shape of a parallelogram, the sides lean into the same direction. */
    private fun parallelogramOf(rect: RectF): Path = Path().apply {
        val lean = rect.width() * 0.2f
        moveTo(rect.left + lean, rect.top)
        lineTo(rect.right, rect.top)
        lineTo(rect.right - lean, rect.bottom)
        lineTo(rect.left, rect.bottom)
        close()
    }

    /** The shape of a star with the given number of points. */
    private fun starOf(rect: RectF, points: Int): Path {
        val path = Path()
        val outer = min(rect.width(), rect.height()) / 2f
        val inner = outer * 0.42f
        val centreX = rect.centerX()
        val centreY = rect.centerY()
        for (index in 0 until points * 2) {
            // Every second corner is deeper, so the shape has its points.
            val radius = if (index % 2 == 0) outer else inner
            val angle = -HALF_TURN + index * (HALF_TURN / points)
            val x = centreX + cos(angle) * radius
            val y = centreY + sin(angle) * radius
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /** The shape of a ring. */
    private fun donutOf(rect: RectF): Path {
        val path = Path()
        path.addOval(rect, Path.Direction.CW)
        val hole = RectF(
            rect.left + rect.width() * 0.25f,
            rect.top + rect.height() * 0.25f,
            rect.right - rect.width() * 0.25f,
            rect.bottom - rect.height() * 0.25f,
        )
        path.addOval(hole, Path.Direction.CCW)
        return path
    }

    /** The shape of a circle with a cut out of it. */
    private fun pieOf(rect: RectF): Path {
        val path = Path()
        path.moveTo(rect.centerX(), rect.centerY())
        path.arcTo(rect, -90f, 270f)
        path.close()
        return path
    }

    /** The shape of a cross. */
    private fun plusOf(rect: RectF): Path {
        val path = Path()
        val arm = min(rect.width(), rect.height()) * 0.3f
        path.addRect(
            rect.left + (rect.width() - arm) / 2f,
            rect.top,
            rect.left + (rect.width() + arm) / 2f,
            rect.bottom,
            Path.Direction.CW,
        )
        path.addRect(
            rect.left,
            rect.top + (rect.height() - arm) / 2f,
            rect.right,
            rect.top + (rect.height() + arm) / 2f,
            Path.Direction.CW,
        )
        return path
    }

    /** The shape of a heart. */
    private fun heartOf(rect: RectF): Path {
        val path = Path()
        val width = rect.width()
        val height = rect.height()
        path.moveTo(rect.centerX(), rect.bottom)
        path.cubicTo(
            rect.left - width * 0.1f, rect.top + height * 0.55f,
            rect.left + width * 0.25f, rect.top - height * 0.1f,
            rect.centerX(), rect.top + height * 0.3f,
        )
        path.cubicTo(
            rect.right - width * 0.25f, rect.top - height * 0.1f,
            rect.right + width * 0.1f, rect.top + height * 0.55f,
            rect.centerX(), rect.bottom,
        )
        path.close()
        return path
    }

    /** The shape of a lightning bolt. */
    private fun lightningOf(rect: RectF): Path = Path().apply {
        moveTo(rect.centerX() + rect.width() * 0.2f, rect.top)
        lineTo(rect.left + rect.width() * 0.15f, rect.top + rect.height() * 0.55f)
        lineTo(rect.centerX(), rect.top + rect.height() * 0.5f)
        lineTo(rect.right - rect.width() * 0.2f, rect.bottom)
        lineTo(rect.right - rect.width() * 0.12f, rect.top + rect.height() * 0.45f)
        lineTo(rect.centerX(), rect.top + rect.height() * 0.5f)
        close()
    }

    /** The shape of a cloud, a row of bumps. */
    private fun cloudOf(rect: RectF): Path {
        val path = Path()
        val radius = min(rect.width(), rect.height()) * 0.25f
        path.addOval(
            RectF(rect.left, rect.top + rect.height() * 0.3f, rect.left + radius * 2, rect.bottom),
            Path.Direction.CW,
        )
        path.addOval(
            RectF(rect.left + rect.width() * 0.2f, rect.top, rect.left + rect.width() * 0.7f, rect.bottom),
            Path.Direction.CW,
        )
        path.addOval(
            RectF(rect.right - radius * 2, rect.top + rect.height() * 0.3f, rect.right, rect.bottom),
            Path.Direction.CW,
        )
        return path
    }

    /** The shape of a drop with a rounded corner. */
    private fun teardropOf(rect: RectF): Path {
        val path = Path()
        path.moveTo(rect.centerX(), rect.top)
        path.cubicTo(
            rect.right, rect.top + rect.height() * 0.55f,
            rect.right, rect.bottom,
            rect.centerX(), rect.bottom,
        )
        path.cubicTo(
            rect.left, rect.bottom,
            rect.left, rect.top + rect.height() * 0.55f,
            rect.centerX(), rect.top,
        )
        path.close()
        return path
    }

    /** The shape of a moon. */
    private fun moonOf(rect: RectF): Path {
        val path = Path()
        path.addOval(rect, Path.Direction.CW)
        val cut = RectF(
            rect.left - rect.width() * 0.1f,
            rect.top - rect.height() * 0.1f,
            rect.left + rect.width() * 0.75f,
            rect.bottom + rect.height() * 0.1f,
        )
        path.addOval(cut, Path.Direction.CCW)
        return path
    }

    private fun drawPicture(canvas: Canvas, shape: PptxShape.Picture, scale: Float) {
        val bitmap = bitmaps.getOrPut(shape) { decode(shape.bytes) ?: return }
        val rect = shape.rect(scale)
        // The picture can be cut away on every side, the cut is taken off the
        // picture before it is put into the frame.
        val source = sourceRectOf(bitmap, shape)
        if (shape.rotation != 0f) {
            canvas.save()
            canvas.rotate(shape.rotation, rect.centerX(), rect.centerY())
        }
        canvas.drawBitmap(bitmap, source, rect, bitmapPaint)
        if (shape.rotation != 0f) canvas.restore()
    }

    /**
     * The part of the picture that is shown, the cut away part is left out.
     */
    private fun sourceRectOf(bitmap: Bitmap, shape: PptxShape.Picture): Rect {
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val left = shape.cropLeft * width
        val top = shape.cropTop * height
        val right = width - shape.cropRight * width
        val bottom = height - shape.cropBottom * height
        if (right <= left || bottom <= top) {
            return Rect(0, 0, bitmap.width, bitmap.height)
        }
        return Rect(
            left.toInt(),
            top.toInt(),
            right.toInt().coerceAtMost(bitmap.width),
            bottom.toInt().coerceAtMost(bitmap.height),
        )
    }

    private fun drawTextBox(canvas: Canvas, shape: PptxShape.TextBox, scale: Float) {
        val rect = shape.rect(scale)
        shape.fill?.let {
            fillPaint.color = OfficeColors.toAndroid(it)
            canvas.drawRect(rect, fillPaint)
        }
        // The office software keeps the text away from the border of the box.
        val textLeft = rect.left + shape.insetLeftDp * scale
        val textTop = rect.top + shape.insetTopDp * scale
        val textWidth = (rect.width() - (shape.insetLeftDp + shape.insetRightDp) * scale)
            .coerceAtLeast(1f)
        val textHeight = (rect.height() - (shape.insetTopDp + shape.insetBottomDp) * scale)
            .coerceAtLeast(0f)
        // The text of a box that is too small is shrunk, the way the office
        // software does it, so it does not lie over the other shapes.
        var factor = shape.fontScale
        var layouts = shape.paragraphs.map { buildLayout(it, textWidth, scale, factor, shape.wrap) }
        var total = totalHeight(shape, layouts)
        var step = 0
        // A box that grows with its text keeps the size of the text.
        val grows = shape.autoFit == "shape"
        while (!grows && total > textHeight && textHeight > 0f && step < SHRINK_STEPS) {
            factor *= SHRINK_FACTOR
            layouts = shape.paragraphs.map {
                buildLayout(it, textWidth, scale, factor, shape.wrap)
            }
            total = totalHeight(shape, layouts)
            step++
        }
        var y = textTop
        if (shape.anchor == "center") {
            y = textTop + (textHeight - total) / 2f
        } else if (shape.anchor == "bottom") {
            y = textTop + textHeight - total
        }
        layouts.forEachIndexed { index, layout ->
            val paragraph = shape.paragraphs.getOrNull(index)
            // The space above the paragraph is part of the flow of the text.
            paragraph?.spaceBeforePt?.let { y += it * scale * 4f / 3f }
            val margin = (paragraph?.marginLeftDp ?: 0f) * scale
            val indent = (paragraph?.indentDp ?: 0f) * scale
            val bullet = paragraph?.bullet
            // A paragraph without a hanging indent keeps its bullet in front
            // of the text, the layout draws it.
            val bulletInText = bullet != null && margin <= 0f
            canvas.save()
            // The text of the paragraph stands behind its left margin, the
            // bullet of a hanging indent stands in front of it.
            canvas.translate(textLeft + margin + shiftOf(paragraph, layout, textWidth, shape.wrap), y)
            layout.draw(canvas)
            canvas.restore()
            if (bullet != null && !bulletInText) {
                bulletPaint.color = paragraph.style.color?.let { OfficeColors.toAndroid(it) }
                    ?: context.getColor(R.color.text_primary)
                bulletPaint.textSize = -layout.getLineAscent(0) * 0.8f
                canvas.drawText(
                    bullet,
                    textLeft + margin + indent,
                    y + layout.getLineBaseline(0),
                    bulletPaint,
                )
            }
            y += layout.height
            // The space below the paragraph is part of the flow of the text.
            paragraph?.spaceAfterPt?.let { y += it * scale * 4f / 3f }
        }
    }

    /**
     * The place of a text that does not wrap.
     *
     * The text of a box that does not wrap runs over its border, the office
     * software puts it into the middle of the box when it is centered.
     */
    private fun shiftOf(
        paragraph: PptxParagraph?,
        layout: android.text.Layout,
        textWidth: Float,
        wrap: Boolean,
    ): Float {
        if (wrap) return 0f
        return when (paragraph?.style?.alignment) {
            "center" -> (textWidth - layout.width) / 2f
            "right" -> textWidth - layout.width
            else -> 0f
        }
    }

    /**
     * The height of the text of a box.
     *
     * The space above and below the paragraphs belongs to it, the office
     * software counts it as well.
     */
    private fun totalHeight(shape: PptxShape.TextBox, layouts: List<android.text.Layout>): Float {
        val text = layouts.sumOf { it.height.toDouble() }.toFloat()
        val space = shape.paragraphs.sumOf { paragraph ->
            (paragraph.spaceBeforePt + paragraph.spaceAfterPt).toDouble()
        }.toFloat()
        return text + space * 4f / 3f
    }

    private fun drawTable(canvas: Canvas, shape: PptxShape.Table, scale: Float) {
        val rect = shape.rect(scale)
        val style = shape.style
        val borderColour = style?.border?.let { OfficeColors.toAndroid(it) }
            ?: context.getColor(R.color.surface_border_strong)
        var y = rect.top
        shape.rows.forEachIndexed { rowIndex, row ->
            val rowHeight = shape.rowHeightsDp.getOrNull(rowIndex)?.times(scale) ?: (24f * scale)
            var x = rect.left
            row.forEachIndexed { columnIndex, cell ->
                val columnWidth = shape.columnWidthsDp.getOrNull(columnIndex)?.times(scale) ?: (80f * scale)
                // The style paints the rows of the table, a cell that has a
                // colour of its own keeps it.
                val background = cell.background
                    ?: styleFillOf(style, rowIndex)
                background?.let {
                    fillPaint.color = OfficeColors.toAndroid(it)
                    canvas.drawRect(x, y, x + columnWidth, y + rowHeight, fillPaint)
                }
                val colour = cell.color?.let { OfficeColors.toAndroid(it) }
                    ?: style?.let { styleTextOf(it, rowIndex) }?.let { OfficeColors.toAndroid(it) }
                    ?: context.getColor(R.color.text_primary)
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = colour
                    // The text of a cell follows the height of its row.
                    textSize = max(6f * scale, rowHeight * 0.45f)
                    typeface = Typeface.create(
                        Typeface.DEFAULT,
                        if (cell.bold || (rowIndex == 0 && style?.firstRowBold == true)) {
                            Typeface.BOLD
                        } else {
                            Typeface.NORMAL
                        },
                    )
                    textAlign = when (cell.alignment) {
                        "center" -> Paint.Align.CENTER
                        "right" -> Paint.Align.RIGHT
                        else -> Paint.Align.LEFT
                    }
                }
                val baseline = y + rowHeight / 2f - (paint.descent() + paint.ascent()) / 2f
                val inset = 4f * scale
                when (cell.alignment) {
                    "center" -> canvas.drawText(cell.text, x + columnWidth / 2f, baseline, paint)
                    "right" -> canvas.drawText(cell.text, x + columnWidth - inset, baseline, paint)
                    else -> canvas.drawText(cell.text, x + inset, baseline, paint)
                }
                x += columnWidth
            }
            linePaint.color = borderColour
            linePaint.strokeWidth = max(density, (style?.borderWidthDp ?: 1f) * scale)
            canvas.drawLine(rect.left, y + rowHeight, rect.right, y + rowHeight, linePaint)
            y += rowHeight
        }
    }

    /** The colour that the style paints a row of the table with. */
    private fun styleFillOf(style: PptxTableStyle?, rowIndex: Int): OfficeColor? {
        if (style == null) return null
        return when {
            rowIndex == 0 -> style.firstRowFill
            rowIndex % 2 == 1 -> style.bandFill
            else -> style.bandAltFill
        }
    }

    /** The colour that the style paints the text of a row with. */
    private fun styleTextOf(style: PptxTableStyle, rowIndex: Int): OfficeColor? =
        if (rowIndex == 0) style.firstRowText else style.text

    /**
     * Builds the layout of one paragraph.
     *
     * Every paragraph gets its own paint: a layout keeps the paint it was
     * built with, so a shared one would draw every paragraph with the size of
     * the last one.
     */
    private fun buildLayout(
        paragraph: PptxParagraph,
        widthPx: Float,
        scale: Float,
        shrink: Float = 1f,
        wrap: Boolean = true,
    ): StaticLayout {
        val style = paragraph.style
        val builder = SpannableStringBuilder()
        // The text stands behind the left margin of its paragraph.
        var margin = paragraph.marginLeftDp * scale
        // A paragraph without a hanging indent keeps its bullet in front of
        // the text, so the bullet becomes a part of the text.
        val bulletInText = paragraph.bullet != null && margin <= 0f
        if (bulletInText) margin = 0f
        if (bulletInText) {
            builder.append(paragraph.bullet.orEmpty())
            builder.append("  ")
        }
        // Every part of the text keeps the style of the file.
        val parts = if (paragraph.runs.isEmpty()) {
            listOf(PptxRun(paragraph.text, style))
        } else {
            paragraph.runs
        }
        val baseSize = style.sizePt.coerceAtLeast(1f)
        parts.forEach { part ->
            val start = builder.length
            builder.append(part.text)
            val end = builder.length
            if (end <= start) return@forEach
            val partStyle = part.style
            if (partStyle.bold || partStyle.italic) {
                val weight = when {
                    partStyle.bold && partStyle.italic -> Typeface.BOLD_ITALIC
                    partStyle.bold -> Typeface.BOLD
                    else -> Typeface.ITALIC
                }
                builder.setSpan(StyleSpan(weight), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (partStyle.underline) {
                builder.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            partStyle.color?.let { colour ->
                builder.setSpan(
                    ForegroundColorSpan(OfficeColors.toAndroid(colour)),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            // A part that is bigger or smaller than the paragraph keeps its
            // size.
            if (abs(partStyle.sizePt - baseSize) > 0.01f) {
                builder.setSpan(
                    RelativeSizeSpan(partStyle.sizePt / baseSize),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.color?.let { OfficeColors.toAndroid(it) }
                ?: context.getColor(R.color.text_primary)
            // One point is 4/3 of a deck dp.
            textSize = max(MIN_TEXT_SIZE_DP * scale, style.sizePt * scale * 4f / 3f) * shrink
            // The family of the deck, the weight of the text comes from the
            // span above it.
            typeface = typefaceOf(style.fontFamily, false, false)
        }
        val alignment = when (style.alignment) {
            "center" -> Layout.Alignment.ALIGN_CENTER
            "right" -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        // A box that does not wrap lets its text run over its border.
        val width = if (wrap) (widthPx - margin).coerceAtLeast(1f) else NO_WRAP_WIDTH
        // The distance between the lines: a file that names it in percent
        // gives a factor, a file that names it in point gives the distance.
        val exact = paragraph.lineSpacingPt?.let { it * scale * 4f / 3f }
        val metrics = paint.fontMetrics
        val natural = (metrics.descent - metrics.ascent).toFloat()
        val extra = if (exact != null) exact - natural else 0f
        return StaticLayout.Builder
            .obtain(builder, 0, builder.length, paint, width.toInt())
            .setAlignment(alignment)
            .setIncludePad(false)
            .setLineSpacing(extra, paragraph.lineSpacing)
            .build()
    }

    /**
     * The typeface of a text.
     *
     * The phone does not carry every font of a deck, the known families are
     * mapped to the ones that look the same.
     */
    private fun typefaceOf(family: String?, bold: Boolean, italic: Boolean): Typeface {
        val base = baseTypefaceOf(family)
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(base, style)
    }

    /** The typeface of a family of fonts. */
    private fun baseTypefaceOf(family: String?): Typeface {
        val name = family?.trim().orEmpty()
        if (name.isEmpty()) return Typeface.DEFAULT
        // The fonts of the chinese decks.
        when {
            name.contains("宋") || name.contains("SimSun") || name.contains("NSimSun") ||
                name.contains("楷") || name.contains("KaiTi") || name.contains("FangSong") ||
                name.contains("仿宋") -> return Typeface.SERIF

            name.contains("黑") || name.contains("SimHei") || name.contains("雅黑") ||
                name.contains("YaHei") || name.contains("等线") || name.contains("DengXian") ||
                name.contains("苹方") -> return Typeface.SANS_SERIF

            name.contains("mono", true) || name.contains("Courier", true) ||
                name.contains("Consolas", true) -> return Typeface.MONOSPACE

            name.contains("script", true) || name.contains("hand", true) -> return Typeface.SERIF
        }
        if (name.contains("serif", true) && !name.contains("sans", true)) return Typeface.SERIF
        if (name.contains("sans", true)) return Typeface.SANS_SERIF
        // An unknown family falls back to the font of the system.
        return Typeface.create(name, Typeface.NORMAL)
    }

    private fun decode(bytes: ByteArray): Bitmap? =
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

    private fun dp(value: Float): Float = value * density

    private companion object {
        /** The step the text of a box that is too small is shrunk by. */
        const val SHRINK_FACTOR = 0.85f

        /** How often the text of a box is shrunk. */
        const val SHRINK_STEPS = 8
        /** The smallest text of a slide, in deck dp. */
        const val MIN_TEXT_SIZE_DP = 8f

        /** The gutter of a bulleted paragraph, in deck dp. */
        const val BULLET_INDENT_DP = 16f

        /**
         * The width of a text that does not wrap.
         *
         * The text runs over the border of its box, so the layout needs a
         * width that never breaks a line.
         */
        const val NO_WRAP_WIDTH = 10000f

        /** Half of a turn, in radian. */
        val HALF_TURN = (PI / 2.0).toFloat()

        /** A whole turn, in radian. */
        val FULL_TURN = PI.toFloat()
    }
}

/** The pixel rectangle of a shape. */
private fun PptxShape.rect(scale: Float): RectF = RectF(
    leftDp * scale,
    topDp * scale,
    (leftDp + widthDp) * scale,
    (topDp + heightDp) * scale,
)
