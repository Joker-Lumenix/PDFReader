package com.peng.pdfreader.appcore.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
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
import com.peng.pdfreader.appcore.pdf.PdfEngine
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.max
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
 */
data class PdfMark(
    val kind: PdfMarkKind,
    val colour: Int,
    val rect: RectF,
    val ink: Path? = null,
)

/**
 * A single PDF page with pinch zoom, pan and the annotation overlay.
 *
 * The page bitmap is rendered by the framework renderer of `:appcore` on a
 * background thread, the view only ever paints what it already owns.
 */
class PdfPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

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

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
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
        requestRender()
    }

    /** Renders a raster image as a single page. */
    fun setImage(file: File) {
        source = file
        pageIndex = 0
        imageDocument = true
        requestRender()
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

    // ------------------------------------------------------------------ rendering

    private fun requestRender() {
        val file = source ?: return
        val index = pageIndex
        val isImage = imageDocument
        val token = ++renderToken
        bitmap = null
        invalidate()
        val targetWidth = if (width > 0) {
            (width * density * 1.6f).toInt().coerceAtLeast(480)
        } else {
            1240
        }
        executor.execute {
            val rendered = if (isImage) {
                PdfEngine.renderImage(file, targetWidth)
            } else {
                PdfEngine.renderPage(file, index, targetWidth)
            }
            mainHandler.post {
                if (token != renderToken) return@post
                if (rendered == null) {
                    onRenderFailed?.invoke()
                    invalidate()
                    return@post
                }
                bitmap = rendered.bitmap
                pageWidthPt = rendered.pageWidthPt
                pageHeightPt = rendered.pageHeightPt
                baseScale = if (width > 0 && height > 0) {
                    min(width.toFloat() / rendered.bitmap.width, height.toFloat() / rendered.bitmap.height)
                } else {
                    0f
                }
                zoom = 1f
                offsetX = 0f
                offsetY = 0f
                invalidate()
                onRendered?.invoke()
            }
        }
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
        drawSelection(canvas)
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
                    inkPaint.strokeWidth = max(1f, 2f / scale / page.width)
                    canvas.save()
                    canvas.scale(page.width.toFloat(), page.height.toFloat())
                    canvas.drawPath(ink, inkPaint)
                    canvas.restore()
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

    // ------------------------------------------------------------------ touch

    override fun onTouchEvent(event: MotionEvent): Boolean {
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        executor.shutdownNow()
    }

    companion object {
        private const val MAX_ZOOM = 5f
    }
}
