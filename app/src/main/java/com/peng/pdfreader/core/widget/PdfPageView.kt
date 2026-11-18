package com.peng.pdfreader.core.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.peng.pdfreader.R
import com.peng.pdfreader.core.pdf.PdfEngine
import com.peng.pdfreader.core.util.Logger
import java.io.File
import java.util.concurrent.RejectedExecutionException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.min

/**
 * Visual reading mode of a PDF page.
 */
enum class ReadingMode {
    LIGHT,
    SEPIA,
    NIGHT_INVERT,
    BLACK,
}

/**
 * The kind of a mark that is drawn on top of a PDF page.
 */
enum class PdfMarkKind {
    HIGHLIGHT,
    UNDERLINE,
    STRIKETHROUGH,
    DRAW,
    NOTE,
    SIGNATURE,
}

/**
 * A mark drawn on top of a rendered page.
 *
 * @param kind what the mark represents
 * @param colour the colour of the mark
 * @param rect the area of the page, expressed as fractions (0..1)
 * @param ink the strokes of a free hand mark, also in fractions
 * @param width the width of a free hand mark, as a fraction of the page width
 */
data class PdfMark(
    val kind: PdfMarkKind,
    val colour: Int,
    val rect: RectF,
    val ink: Path? = null,
    val width: Float = 0f,
)

/**
 * A single PDF page with pinch zoom, pan and the annotation overlay.
 *
 * The page bitmap is rendered by the framework renderer of the core package on a
 * background thread, the view only ever paints what it already owns. The pool
 * of the engine is shared by every page of the app, so a view that is
 * recycled and bound again keeps working.
 */
class PdfPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    /** How close a finger has to be to hit a round button. */
    private val buttonTouchRadius = 14f * density

    /** How far a finger has to travel to turn the page while drawing. */
    private val pageSwipeDistance = 28f * density

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#fb4141")
        strokeWidth = density
        pathEffect = DashPathEffect(floatArrayOf(4f * density, 3f * density), 0f)
    }
    private val selectionFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#33fb4141")
    }
    private val placementFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        // The design lays a faint red film inside the frame of the signature.
        color = Color.parseColor("#0dfb4141")
    }
    private val placementBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        // The frame of the design is a red line around the signature.
        color = Color.parseColor("#fb4141")
        strokeWidth = 2f * density
    }
    private val placementInkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pageBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        // The label of the page of the design: a half transparent black tile.
        color = Color.parseColor("#80121111")
    }
    private val pageBadgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * density
        textAlign = Paint.Align.CENTER
    }

    /** The red cross that throws the placed signature away. */
    private val deleteIcon: Bitmap? = BitmapFactory.decodeResource(resources, R.mipmap.ic_close_red)

    /** The arrows that scale the placed signature. */
    private val resizeIcon: Bitmap? = BitmapFactory.decodeResource(resources, R.mipmap.ic_drag)

    /** Whether the number of the page is painted on it (design: sign-1). */
    var showPageBadge: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var source: File? = null
    private var pageIndex = 0
    private var imageDocument = false
    private var renderToken = 0
    private var bitmap: Bitmap? = null

    private var marks: List<PdfMark> = emptyList()
    private var readingMode: ReadingMode = ReadingMode.LIGHT
    private var zoom = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var baseScale = 0f
    private val pageRect = RectF()

    private var selectionEnabled = false
    private var selectionStartX = 0f
    private var selectionStartY = 0f
    private val selectionRect = RectF()
    private var hasSelection = false

    private var placementStrokes: List<InkStroke>? = null
    private var placementAspect = 1f
    private val placementBox = RectF()
    private var placementActive = false
    private var draggingPlacement = false
    private var resizingPlacement = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // ------------------------------------------------------------------ free hand ink

    /** The ink that lies on the page, expressed as fractions of the page. */
    private var inkStrokes: List<InkStroke> = emptyList()

    /** The stroke that is being drawn, in screen pixels. */
    private val inkPath = Path()
    private val inkPoints = ArrayList<Float>()
    private var inkDrawing = false
    private var inkSwipe = false
    private var inkStartX = 0f
    private var inkStartY = 0f
    private var inkLastX = 0f
    private var inkLastY = 0f

    /** Whether the user may draw on the page. */
    var inkEnabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) resetInk()
            invalidate()
        }

    /** Colour of the next stroke. */
    var inkColour: Int = Color.parseColor("#fb4141")

    /** Width of the next stroke, in pixels. */
    var inkWidth: Float = 3f * density

    /** Raised with the stroke the user has just drawn, in page fractions. */
    var onInkStroke: ((InkStroke) -> Unit)? = null

    /** Raised when a swipe asks for another page, -1 or 1. */
    var onPageSwipe: ((Int) -> Unit)? = null

    /** Raised when the user throws the placed signature away. */
    var onPlacementCancelled: (() -> Unit)? = null

    /** Whether a signature waits for its confirmation on this page. */
    val hasPendingPlacement: Boolean get() = placementActive

    /** Raised when the page has been rendered. */
    var onRendered: (() -> Unit)? = null

    /** Raised when the page could not be rendered. */
    var onRenderFailed: (() -> Unit)? = null

    /** Raised when the user taps the page. */
    var onTap: (() -> Unit)? = null

    /** Width of the rendered page in points. */
    var pageWidthPt: Int = 595
        private set

    /** Height of the rendered page in points. */
    var pageHeightPt: Int = 842
        private set

    /**
     * Raised with the selected area of the page, expressed as fractions (0..1).
     *
     * Only fires while [selectionEnabled] is on.
     */
    var onSelection: ((RectF) -> Unit)? = null

    /** Whether the user may drag out a selection area. */
    var isSelectionEnabled: Boolean
        get() = selectionEnabled
        set(value) {
            selectionEnabled = value
            if (!value) clearSelection()
            invalidate()
        }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                applyScale(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                // The gesture is over: the page can be rendered again with the
                // detail the new zoom asks for.
                requestSharperPage()
            }
        },
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onTap?.invoke()
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                offsetX -= distanceX
                offsetY -= distanceY
                clampOffset()
                invalidate()
                return true
            }
        },
    )

    init {
        isClickable = true
    }

    // ------------------------------------------------------------------ api

    /** Renders a page of a PDF document. */
    fun setPage(file: File, index: Int) {
        source = file
        pageIndex = index
        imageDocument = false
        requestRender(resetViewport = true)
    }

    /** Renders a raster image as a single page. */
    fun setImage(file: File) {
        source = file
        pageIndex = 0
        imageDocument = true
        requestRender(resetViewport = true)
    }

    /**
     * Gives the bitmap of the page back.
     *
     * The bitmap is the largest object the reader owns, so it is freed as
     * soon as the view is not painted any more.
     */
    fun release() {
        // A render that is still on its way belongs to the old page.
        renderToken++
        releaseBitmap()
        invalidate()
    }

    /** Switches the visual reading mode. */
    fun setReadingMode(mode: ReadingMode) {
        if (readingMode == mode) return
        readingMode = mode
        invalidate()
    }

    /** The marks painted on top of the page. */
    fun setMarks(value: List<PdfMark>) {
        marks = value
        invalidate()
    }

    /** Removes the current selection area. */
    fun clearSelection() {
        hasSelection = false
        selectionRect.setEmpty()
        invalidate()
    }

    // ------------------------------------------------------------------ signature placement

    /**
     * Puts a signature on the page and lets the user drag it into place.
     *
     * The signature is shown inside the selection box of the design: a light
     * grey frame with a delete button on the top left corner and a resize
     * button on the bottom right corner.
     *
     * @param strokes the ink, normalised against the box of the signature
     * @param aspect the width / height ratio of the signature
     */
    fun beginSignaturePlacement(strokes: List<InkStroke>, aspect: Float) {
        if (strokes.isEmpty()) return
        placementStrokes = strokes
        placementAspect = if (aspect > 0f) aspect else 1f
        placementActive = true
        val page = bitmap
        val pageAspect = if (page != null && page.height > 0) {
            page.width.toFloat() / page.height.toFloat()
        } else {
            0.707f
        }
        val width = PLACEMENT_WIDTH
        val height = (width * pageAspect / placementAspect).coerceIn(0.05f, 0.5f)
        placementBox.set(PLACEMENT_LEFT, PLACEMENT_TOP, PLACEMENT_LEFT + width, PLACEMENT_TOP + height)
        invalidate()
    }

    /** Removes the signature that waits for its confirmation. */
    fun cancelSignaturePlacement() {
        if (!placementActive) return
        placementActive = false
        placementStrokes = null
        draggingPlacement = false
        resizingPlacement = false
        invalidate()
        onPlacementCancelled?.invoke()
    }

    /**
     * Hands the placed signature over and takes it off the page.
     *
     * The strokes are expressed as fractions of the page, like every other
     * mark of the document.
     */
    fun commitSignaturePlacement(): List<InkStroke>? {
        if (placementStrokes == null) return null
        val placed = placedStrokes()
        placementActive = false
        placementStrokes = null
        draggingPlacement = false
        resizingPlacement = false
        invalidate()
        return placed
    }

    /** The strokes of the placed signature, in page fractions. */
    private fun placedStrokes(): List<InkStroke> {
        val strokes = placementStrokes ?: return emptyList()
        return strokes.map { stroke ->
            val points = FloatArray(stroke.points.size)
            var index = 0
            while (index + 1 < stroke.points.size) {
                points[index] = placementBox.left + stroke.points[index] * placementBox.width()
                points[index + 1] = placementBox.top + stroke.points[index + 1] * placementBox.height()
                index += 2
            }
            // The width of a stroke is a fraction of the signature box.
            InkStroke(stroke.color, stroke.width * placementBox.width(), points)
        }
    }

    // ------------------------------------------------------------------ free hand ink

    /** Puts the ink of the page on it. */
    fun setInk(strokes: List<InkStroke>) {
        inkStrokes = strokes
        invalidate()
    }

    /** Renders the page again, e.g. after the document was written. */
    fun refresh() {
        requestRender(resetViewport = false)
    }

    // ------------------------------------------------------------------ rendering

    /**
     * Renders the page on a worker thread.
     *
     * @param resetViewport whether the zoom and the pan of the page start
     * over. A new page always starts over, a page that is only rendered again
     * to get more detail keeps the place of the reader.
     */
    private fun requestRender(resetViewport: Boolean) {
        val file = source ?: return
        val index = pageIndex
        val isImage = imageDocument
        val token = ++renderToken
        if (resetViewport) releaseBitmap()
        invalidate()
        val targetWidth = renderWidth()
        try {
            PdfEngine.workers.execute {
                val rendered = if (isImage) {
                    PdfEngine.renderImage(file, targetWidth)
                } else {
                    PdfEngine.renderPage(file, index, targetWidth)
                }
                mainHandler.post {
                    if (token != renderToken) {
                        // The page was replaced while the render was running.
                        recycle(rendered?.bitmap)
                        return@post
                    }
                    if (rendered == null) {
                        onRenderFailed?.invoke()
                        invalidate()
                        return@post
                    }
                    val previous = bitmap
                    bitmap = rendered.bitmap
                    if (previous != null && previous !== rendered.bitmap) recycle(previous)
                    pageWidthPt = rendered.pageWidthPt
                    pageHeightPt = rendered.pageHeightPt
                    baseScale = if (width > 0 && height > 0) {
                        min(width.toFloat() / rendered.bitmap.width, height.toFloat() / rendered.bitmap.height)
                    } else {
                        0f
                    }
                    if (resetViewport) {
                        zoom = 1f
                        offsetX = 0f
                        offsetY = 0f
                    } else {
                        clampOffset()
                    }
                    invalidate()
                    onRendered?.invoke()
                }
            }
        } catch (error: RejectedExecutionException) {
            Logger.w(TAG, "the render pool does not accept the page any more")
        }
    }

    /**
     * The width of the bitmap the page is rendered into.
     *
     * The view measures its pages in pixels, so the width of the bitmap is
     * derived from the width of the view and never from its density: a
     * density of three would ask for a bitmap that is three times as wide as
     * the screen and would not fit into the heap of a phone.
     */
    private fun renderWidth(): Int {
        val viewWidth = if (width > 0) width else resources.displayMetrics.widthPixels
        val wanted = (viewWidth * zoom * RENDER_SCALE).toInt()
        return wanted.coerceIn(MIN_RENDER_WIDTH, MAX_RENDER_WIDTH)
    }

    /**
     * Renders the page again when the reader zoomed in far enough.
     *
     * The page is drawn from a bitmap, so a zoom that needs more pixels than
     * the bitmap holds would only blur it. The page is therefore rendered
     * again as soon as the zoom asks for more detail than the current bitmap
     * offers.
     */
    private fun requestSharperPage() {
        val page = bitmap ?: return
        if (zoom <= 1f) return
        if (renderWidth() <= page.width) return
        requestRender(resetViewport = false)
    }

    /** Drops the bitmap of the page. */
    private fun releaseBitmap() {
        val old = bitmap
        bitmap = null
        recycle(old)
    }

    /** Frees a bitmap that is not painted any more. */
    private fun recycle(target: Bitmap?) {
        if (target == null) return
        if (target.isRecycled) return
        target.recycle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val page = bitmap ?: return
        baseScale = min(w.toFloat() / page.width, h.toFloat() / page.height)
    }

    override fun onDraw(canvas: Canvas) {
        val page = bitmap
        if (page == null) {
            canvas.drawColor(Color.parseColor("#f6f7fb"))
            return
        }
        if (baseScale <= 0f) {
            baseScale = min(width.toFloat() / page.width, height.toFloat() / page.height)
        }
        val scale = baseScale * zoom
        val left = (width - page.width * scale) / 2f + offsetX
        val top = (height - page.height * scale) / 2f + offsetY
        pageRect.set(left, top, left + page.width * scale, top + page.height * scale)

        if (readingMode == ReadingMode.BLACK) {
            canvas.drawColor(Color.BLACK)
        } else {
            bitmapPaint.colorFilter = colorFilterFor(readingMode)
            canvas.drawBitmap(page, null, pageRect, bitmapPaint)
            bitmapPaint.colorFilter = null
        }

        drawMarks(canvas, page, scale)
        drawInk(canvas, page, scale)
        drawPageBadge(canvas)
        drawSelection(canvas)
        drawPlacement(canvas)
    }

    private fun drawMarks(canvas: Canvas, page: Bitmap, scale: Float) {
        if (marks.isEmpty()) return
        canvas.save()
        canvas.translate(pageRect.left, pageRect.top)
        canvas.scale(scale, scale)
        marks.forEach { mark ->
            val rect = RectF(
                mark.rect.left * page.width,
                mark.rect.top * page.height,
                mark.rect.right * page.width,
                mark.rect.bottom * page.height,
            )
            when (mark.kind) {
                PdfMarkKind.HIGHLIGHT -> {
                    fillPaint.color = withAlpha(mark.colour, 0.32f)
                    canvas.drawRect(rect, fillPaint)
                }

                PdfMarkKind.UNDERLINE -> {
                    linePaint.color = mark.colour
                    linePaint.strokeWidth = max(1f, 2f / scale)
                    canvas.drawLine(rect.left, rect.bottom, rect.right, rect.bottom, linePaint)
                }

                PdfMarkKind.STRIKETHROUGH -> {
                    linePaint.color = mark.colour
                    linePaint.strokeWidth = max(1f, 2f / scale)
                    val middle = rect.centerY()
                    canvas.drawLine(rect.left, middle, rect.right, middle, linePaint)
                }

                PdfMarkKind.DRAW, PdfMarkKind.SIGNATURE -> {
                    val ink = mark.ink ?: return@forEach
                    inkPaint.color = mark.colour
                    // The width of the stroke is a fraction of the page width.
                    inkPaint.strokeWidth = (mark.width * page.width).coerceAtLeast(1f)
                    val scaled = Path(ink)
                    val matrix = Matrix()
                    matrix.setScale(page.width.toFloat(), page.height.toFloat())
                    scaled.transform(matrix)
                    canvas.drawPath(scaled, inkPaint)
                }

                PdfMarkKind.NOTE -> {
                    fillPaint.color = mark.colour
                    val size = 18f * density
                    canvas.drawRoundRect(
                        rect.left,
                        rect.top,
                        rect.left + size,
                        rect.top + size,
                        3f * density,
                        3f * density,
                        fillPaint,
                    )
                }
            }
        }
        canvas.restore()
    }

    private fun drawSelection(canvas: Canvas) {
        if (!hasSelection) return
        canvas.drawRect(selectionRect, selectionFillPaint)
        canvas.drawRect(selectionRect, selectionPaint)
    }

    /**
     * Draws the signature that waits for its confirmation.
     *
     * The frame of the design is a red line with a faint red film inside it,
     * a delete button on the top left corner and a resize button on the
     * bottom right corner.
     */
    private fun drawPlacement(canvas: Canvas) {
        val strokes = placementStrokes ?: return
        val box = placementBoxOnScreen()
        // The film of the design sits two pixels inside the frame.
        canvas.drawRect(inset(box, 2f * density), placementFillPaint)
        canvas.drawRect(box, placementBorderPaint)

        strokes.forEach { stroke ->
            placementInkPaint.color = stroke.color
            placementInkPaint.strokeWidth = (stroke.width * box.width()).coerceAtLeast(1f)
            val path = Path()
            var first = true
            var index = 0
            while (index + 1 < stroke.points.size) {
                val x = box.left + stroke.points[index] * box.width()
                val y = box.top + stroke.points[index + 1] * box.height()
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                index += 2
            }
            canvas.drawPath(path, placementInkPaint)
        }

        drawPlacementButton(canvas, box.left, box.top, deleteIcon)
        drawPlacementButton(canvas, box.right, box.bottom, resizeIcon)
    }

    /**
     * Draws one of the round buttons of the selection box.
     *
     * The buttons are the icons of the app: the red cross throws the
     * signature away, the arrows scale it.
     */
    private fun drawPlacementButton(canvas: Canvas, cx: Float, cy: Float, icon: Bitmap?) {
        if (icon == null) return
        val size = 18f * density
        val rect = RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
        canvas.drawBitmap(icon, null, rect, bitmapPaint)
    }

    /**
     * Draws the label of the page (design: sign-1).
     *
     * The design puts a small dark tile with the number of the page in the
     * top left corner of every page.
     */
    private fun drawPageBadge(canvas: Canvas) {
        if (!showPageBadge || bitmap == null) return
        val left = pageRect.left + 8f * density
        val top = pageRect.top + 8f * density
        val rect = RectF(left, top, left + 31f * density, top + 18f * density)
        canvas.drawRoundRect(rect, 4f * density, 4f * density, pageBadgePaint)
        val metrics = pageBadgeTextPaint.fontMetrics
        val baseline = rect.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText("P${pageIndex + 1}", rect.centerX(), baseline, pageBadgeTextPaint)
    }

    /**
     * Draws the ink of the page.
     *
     * The strokes are fractions of the page, so they follow the page while it
     * is zoomed or panned.
     */
    private fun drawInk(canvas: Canvas, page: Bitmap, scale: Float) {
        if (inkStrokes.isNotEmpty()) {
            canvas.save()
            canvas.translate(pageRect.left, pageRect.top)
            canvas.scale(scale, scale)
            inkStrokes.forEach { stroke ->
                if (stroke.isEmpty) return@forEach
                inkPaint.color = stroke.color
                // The width of a stroke is a fraction of the page width.
                inkPaint.strokeWidth = (stroke.width * page.width).coerceAtLeast(1f)
                val path = Path()
                var first = true
                var index = 0
                while (index + 1 < stroke.points.size) {
                    val x = stroke.points[index] * page.width
                    val y = stroke.points[index + 1] * page.height
                    if (first) {
                        path.moveTo(x, y)
                        first = false
                    } else {
                        path.lineTo(x, y)
                    }
                    index += 2
                }
                canvas.drawPath(path, inkPaint)
            }
            canvas.restore()
        }
        if (inkDrawing) {
            // The stroke of the finger is still in screen pixels.
            inkPaint.color = inkColour
            inkPaint.strokeWidth = inkWidth
            canvas.drawPath(inkPath, inkPaint)
        }
    }

    /** A copy of the rectangle, pulled in on every side. */
    private fun inset(rect: RectF, amount: Float): RectF = RectF(
        rect.left + amount,
        rect.top + amount,
        rect.right - amount,
        rect.bottom - amount,
    )

    /**
     * The selection box of the placement, in screen pixels.
     */
    private fun placementBoxOnScreen(): RectF = RectF(
        pageRect.left + placementBox.left * pageRect.width(),
        pageRect.top + placementBox.top * pageRect.height(),
        pageRect.left + placementBox.right * pageRect.width(),
        pageRect.top + placementBox.bottom * pageRect.height(),
    )

    // ------------------------------------------------------------------ touch

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (inkEnabled) {
            return handleInkTouch(event)
        }
        if (placementActive) {
            return handlePlacementTouch(event)
        }
        if (selectionEnabled) {
            return handleSelectionTouch(event)
        }
        if (zoom > 1f) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    /**
     * Draws a stroke on the page.
     *
     * A swipe that runs across the page turns it instead of drawing, so the
     * reader can walk through the document while the drawing is on.
     */
    private fun handleInkTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // The stroke belongs to the page, the pager is only let go
                // when the gesture clearly asks for another page.
                parent?.requestDisallowInterceptTouchEvent(true)
                inkStartX = event.x
                inkStartY = event.y
                inkLastX = event.x
                inkLastY = event.y
                inkDrawing = false
                inkSwipe = false
                inkPath.reset()
                inkPoints.clear()
                inkPoints.add(event.x)
                inkPoints.add(event.y)
                inkPath.moveTo(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - inkStartX
                val dy = event.y - inkStartY
                if (!inkDrawing && !inkSwipe && abs(dx) > pageSwipeDistance) {
                    if (abs(dx) > abs(dy)) {
                        // The gesture turns the page, the stroke is dropped.
                        inkSwipe = true
                        inkPath.reset()
                        inkPoints.clear()
                        invalidate()
                        return true
                    }
                    inkDrawing = true
                }
                if (inkSwipe) return true
                if (!inkDrawing && abs(dy) > pageSwipeDistance) inkDrawing = true
                if (!inkDrawing) return true
                val midX = (inkLastX + event.x) / 2f
                val midY = (inkLastY + event.y) / 2f
                inkPath.quadTo(inkLastX, inkLastY, midX, midY)
                inkPoints.add(event.x)
                inkPoints.add(event.y)
                inkLastX = event.x
                inkLastY = event.y
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (inkSwipe) {
                    val dx = event.x - inkStartX
                    onPageSwipe?.invoke(if (dx < 0f) 1 else -1)
                } else if (inkDrawing) {
                    commitInkStroke()
                }
                resetInk()
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                resetInk()
                invalidate()
                return true
            }
        }
        return true
    }

    /**
     * Hands the stroke of the finger over to the host.
     *
     * The points are fractions of the page, so the stroke stays where it was
     * drawn while the page is zoomed, panned or turned.
     */
    private fun commitInkStroke() {
        val page = bitmap ?: return
        val pageWidth = pageRect.width().coerceAtLeast(1f)
        val pageHeight = pageRect.height().coerceAtLeast(1f)
        if (inkPoints.size < 4) return
        val points = FloatArray(inkPoints.size)
        var index = 0
        while (index + 1 < inkPoints.size) {
            points[index] = ((inkPoints[index] - pageRect.left) / pageWidth).coerceIn(0f, 1f)
            points[index + 1] = ((inkPoints[index + 1] - pageRect.top) / pageHeight).coerceIn(0f, 1f)
            index += 2
        }
        onInkStroke?.invoke(InkStroke(inkColour, inkWidth / pageWidth, points))
    }

    /** Drops the stroke that is being drawn. */
    private fun resetInk() {
        inkDrawing = false
        inkSwipe = false
        inkPath.reset()
        inkPoints.clear()
    }

    /**
     * Moves the placed signature, resizes it or throws it away.
     *
     * A finger that lands outside of the box belongs to the page, so the
     * reader can still be scrolled while a signature waits.
     */
    private fun handlePlacementTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val box = placementBoxOnScreen()
                val delete = distance(event.x, event.y, box.left, box.top)
                val resize = distance(event.x, event.y, box.right, box.bottom)
                if (delete <= buttonTouchRadius) {
                    cancelSignaturePlacement()
                    return true
                }
                if (resize <= buttonTouchRadius) {
                    // The pager must not steal the drag of the handle.
                    parent?.requestDisallowInterceptTouchEvent(true)
                    resizingPlacement = true
                    lastTouchX = event.x
                    lastTouchY = event.y
                    return true
                }
                if (!box.contains(event.x, event.y)) {
                    // The page keeps the gesture, the reader can be scrolled.
                    return false
                }
                // The pager must not steal the drag of the signature.
                parent?.requestDisallowInterceptTouchEvent(true)
                draggingPlacement = true
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (resizingPlacement) {
                    val dx = event.x - lastTouchX
                    resizePlacement(dx)
                    lastTouchX = event.x
                    lastTouchY = event.y
                } else if (draggingPlacement) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    movePlacement(dx, dy)
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingPlacement = false
                resizingPlacement = false
                return true
            }
        }
        return true
    }

    /** Moves the selection box, the signature stays inside the page. */
    private fun movePlacement(dx: Float, dy: Float) {
        val pageWidth = pageRect.width().coerceAtLeast(1f)
        val pageHeight = pageRect.height().coerceAtLeast(1f)
        val left = (placementBox.left + dx / pageWidth).coerceIn(0f, 1f - placementBox.width())
        val top = (placementBox.top + dy / pageHeight).coerceIn(0f, 1f - placementBox.height())
        placementBox.offset(left - placementBox.left, top - placementBox.top)
        invalidate()
    }

    /**
     * Scales the signature.
     *
     * The handle sits in the bottom right corner, so the box grows from its
     * top left corner and keeps the shape of the signature.
     */
    private fun resizePlacement(dx: Float) {
        val pageWidth = pageRect.width().coerceAtLeast(1f)
        val pageHeight = pageRect.height().coerceAtLeast(1f)
        val pageAspect = pageWidth / pageHeight
        var width = placementBox.width() + dx / pageWidth
        width = width.coerceIn(MIN_PLACEMENT_WIDTH, 1f - placementBox.left)
        val height = (width * pageAspect / placementAspect).coerceIn(0.03f, 0.8f)
        var left = placementBox.left
        var top = placementBox.top
        if (left + width > 1f) left = 1f - width
        if (top + height > 1f) top = 1f - height
        placementBox.set(left, top, left + width, top + height)
        invalidate()
    }

    private fun distance(x: Float, y: Float, cx: Float, cy: Float): Float {
        val dx = x - cx
        val dy = y - cy
        return sqrt(dx * dx + dy * dy)
    }

    private fun handleSelectionTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                selectionStartX = event.x
                selectionStartY = event.y
                hasSelection = false
                selectionRect.setEmpty()
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                selectionRect.set(
                    min(selectionStartX, event.x),
                    min(selectionStartY, event.y),
                    max(selectionStartX, event.x),
                    max(selectionStartY, event.y),
                )
                hasSelection = true
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                selectionRect.set(
                    min(selectionStartX, event.x),
                    min(selectionStartY, event.y),
                    max(selectionStartX, event.x),
                    max(selectionStartY, event.y),
                )
                hasSelection = selectionRect.width() > 4f && selectionRect.height() > 4f
                invalidate()
                if (hasSelection) {
                    onSelection?.invoke(pageFractionOf(selectionRect))
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                clearSelection()
                return true
            }
        }
        return true
    }

    private fun pageFractionOf(rect: RectF): RectF {
        val page = bitmap ?: return RectF()
        val width = pageRect.width().coerceAtLeast(1f)
        val height = pageRect.height().coerceAtLeast(1f)
        return RectF(
            ((rect.left - pageRect.left) / width).coerceIn(0f, 1f),
            ((rect.top - pageRect.top) / height).coerceIn(0f, 1f),
            ((rect.right - pageRect.left) / width).coerceIn(0f, 1f),
            ((rect.bottom - pageRect.top) / height).coerceIn(0f, 1f),
        )
    }

    // ------------------------------------------------------------------ zoom

    private fun applyScale(factor: Float, focusX: Float, focusY: Float) {
        val page = bitmap ?: return
        if (baseScale <= 0f) return
        val oldScale = baseScale * zoom
        val newZoom = (zoom * factor).coerceIn(1f, MAX_ZOOM)
        val newScale = baseScale * newZoom
        if (newZoom == zoom) return
        val oldLeft = (width - page.width * oldScale) / 2f + offsetX
        val oldTop = (height - page.height * oldScale) / 2f + offsetY
        val pageX = (focusX - oldLeft) / oldScale
        val pageY = (focusY - oldTop) / oldScale
        zoom = newZoom
        val newLeft = focusX - pageX * newScale
        val newTop = focusY - pageY * newScale
        offsetX = newLeft - (width - page.width * newScale) / 2f
        offsetY = newTop - (height - page.height * newScale) / 2f
        clampOffset()
        invalidate()
    }

    private fun clampOffset() {
        val page = bitmap ?: return
        val scale = baseScale * zoom
        val drawWidth = page.width * scale
        val drawHeight = page.height * scale
        offsetX = if (drawWidth <= width) 0f else offsetX.coerceIn(width - drawWidth, 0f)
        offsetY = if (drawHeight <= height) 0f else offsetY.coerceIn(height - drawHeight, 0f)
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val value = (alpha.coerceIn(0f, 1f) * 255f).toInt()
        return (value shl 24) or (color and 0x00FFFFFF)
    }

    private fun colorFilterFor(mode: ReadingMode): ColorMatrixColorFilter? = when (mode) {
        ReadingMode.LIGHT -> null
        ReadingMode.SEPIA -> ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )

        ReadingMode.NIGHT_INVERT -> ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )

        ReadingMode.BLACK -> null
    }

    companion object {
        private const val TAG = "PdfPageView"

        private const val MAX_ZOOM = 5f

        /**
         * How much detail a page is rendered with.
         *
         * The page is rendered a bit wider than the view, so the reader can
         * zoom in without the page turning blurry right away.
         */
        private const val RENDER_SCALE = 1.5f

        /** The narrowest bitmap a page is rendered into. */
        private const val MIN_RENDER_WIDTH = 480

        /** The widest bitmap a page is rendered into. */
        private const val MAX_RENDER_WIDTH = 2048

        /** The width of a placed signature, as a fraction of the page width. */
        private const val PLACEMENT_WIDTH = 0.414f

        /** The narrowest a placed signature may be scaled. */
        private const val MIN_PLACEMENT_WIDTH = 0.12f

        /** The default position of the selection box on the page. */
        private const val PLACEMENT_LEFT = 0.51f
        private const val PLACEMENT_TOP = 0.704f
    }
}
