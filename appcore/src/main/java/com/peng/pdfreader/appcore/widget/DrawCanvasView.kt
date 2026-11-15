package com.peng.pdfreader.appcore.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Free hand canvas of the annotation editor.
 *
 * The view paints an optional page bitmap and collects the ink on top of it.
 * The strokes are kept per page so a document can be annotated page by page;
 * [exportOverlay] renders the ink of the current page on a transparent bitmap
 * together with the bounds it occupies, which is exactly what
 * `PdfEngine.flattenOverlay` expects.
 */
class DrawCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private class Stroke(
        val path: Path,
        val points: FloatArray,
        val color: Int,
        val width: Float,
    )

    /** The overlay produced by [exportOverlay]. */
    data class Overlay(val bitmap: Bitmap, val bounds: RectF)

    private val density = resources.displayMetrics.density

    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }

    private val pages = LinkedHashMap<Int, MutableList<Stroke>>()
    private val redoStacks = HashMap<Int, MutableList<Stroke>>()
    private val activePoints = ArrayList<Float>()
    private var active: Path? = null
    private var lastX = 0f
    private var lastY = 0f

    /** The page that is painted below the ink. */
    var pageBitmap: Bitmap? = null
        set(value) {
            field = value
            updatePageRect()
            invalidate()
        }

    /** Width of the painted page in points, used for the overlay bounds. */
    var pageWidthPt: Float = 595f

    /** Height of the painted page in points, used for the overlay bounds. */
    var pageHeightPt: Float = 842f

    /** Whether the page bitmap is painted at all. */
    var showPage: Boolean = true

    /** Opacity of the ink, the design paints the strokes at 50 %. */
    var inkAlpha: Float = 0.5f

    /** Colour of the next stroke. */
    var strokeColor: Int = Color.parseColor("#fb4141")
        set(value) {
            field = value
        }

    /** Width of the next stroke, in pixels. */
    var strokeWidth: Float = dp(3f)
        set(value) {
            field = value.coerceAtLeast(1f)
        }

    /** Index of the page the ink belongs to. */
    var pageIndex: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    /** Raised whenever the ink changes. */
    var onStrokesChanged: (() -> Unit)? = null

    val hasInk: Boolean get() = strokesOf(pageIndex).isNotEmpty()
    val canUndo: Boolean get() = strokesOf(pageIndex).isNotEmpty()
    val canRedo: Boolean get() = redoStacks[pageIndex]?.isNotEmpty() == true

    private val pageRect = RectF()

    init {
        isClickable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updatePageRect()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val path = Path().apply { moveTo(event.x, event.y) }
                active = path
                activePoints.clear()
                activePoints.add(event.x)
                activePoints.add(event.y)
                lastX = event.x
                lastY = event.y
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val path = active ?: return true
                val midX = (lastX + event.x) / 2f
                val midY = (lastY + event.y) / 2f
                path.quadTo(lastX, lastY, midX, midY)
                activePoints.add(event.x)
                activePoints.add(event.y)
                lastX = event.x
                lastY = event.y
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                val path = active
                active = null
                if (path != null && activePoints.size >= 2) {
                    path.lineTo(lastX, lastY)
                    strokesOf(pageIndex).add(
                        Stroke(path, activePoints.toFloatArray(), strokeColor, strokeWidth),
                    )
                    redoStacks[pageIndex]?.clear()
                    onStrokesChanged?.invoke()
                }
                activePoints.clear()
                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                active = null
                activePoints.clear()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        val bitmap = pageBitmap
        if (bitmap != null && showPage) {
            canvas.drawBitmap(bitmap, null, pageRect, pagePaint)
        }
        drawStrokes(canvas, strokesOf(pageIndex))
        active?.let { path ->
            inkPaint.color = withAlpha(strokeColor)
            inkPaint.strokeWidth = strokeWidth
            canvas.drawPath(path, inkPaint)
        }
    }

    /** Number of strokes the given page carries. */
    fun strokeCount(index: Int): Int = pages[index]?.size ?: 0

    private fun strokesOf(index: Int): MutableList<Stroke> = pages.getOrPut(index) { ArrayList() }

    /** Removes the last stroke of the current page. */
    fun undo() {
        val list = pages[pageIndex] ?: return
        val stroke = list.removeLastOrNull() ?: return
        redoStacks.getOrPut(pageIndex) { ArrayList() }.add(stroke)
        onStrokesChanged?.invoke()
        invalidate()
    }

    /** Restores the last undone stroke of the current page. */
    fun redo() {
        val redo = redoStacks[pageIndex] ?: return
        val stroke = redo.removeLastOrNull() ?: return
        strokesOf(pageIndex).add(stroke)
        onStrokesChanged?.invoke()
        invalidate()
    }

    /** Drops every stroke of the current page. */
    fun clear() {
        pages.remove(pageIndex)
        redoStacks.remove(pageIndex)
        onStrokesChanged?.invoke()
        invalidate()
    }

    /** The strokes of the current page, normalised against the canvas size. */
    fun inkStrokes(): List<InkStroke> {
        val canvasWidth = width.toFloat().coerceAtLeast(1f)
        val canvasHeight = height.toFloat().coerceAtLeast(1f)
        return strokesOf(pageIndex).map { stroke ->
            val points = FloatArray(stroke.points.size)
            var index = 0
            while (index + 1 < stroke.points.size) {
                points[index] = stroke.points[index] / canvasWidth
                points[index + 1] = stroke.points[index + 1] / canvasHeight
                index += 2
            }
            InkStroke(stroke.color, stroke.width / canvasWidth, points)
        }
    }

    /**
     * The strokes of the current page, expressed as fractions of the painted
     * page - this is the space the annotations of a document live in.
     */
    fun pageInkStrokes(): List<InkStroke> {
        val rect = pageRect
        if (rect.width() <= 0f || rect.height() <= 0f) return inkStrokes()
        return strokesOf(pageIndex).map { stroke ->
            val points = FloatArray(stroke.points.size)
            var index = 0
            while (index + 1 < stroke.points.size) {
                points[index] = (stroke.points[index] - rect.left) / rect.width()
                points[index + 1] = (stroke.points[index + 1] - rect.top) / rect.height()
                index += 2
            }
            InkStroke(stroke.color, stroke.width / rect.width(), points)
        }
    }

    /**
     * Renders the ink of the current page on a transparent bitmap.
     *
     * The returned bounds live in the coordinate space `PdfEngine.flattenOverlay`
     * expects: a page of 595 points wide, with the height derived from the
     * aspect ratio of the page.
     */
    fun exportOverlay(): Overlay? {
        val strokes = pages[pageIndex].orEmpty()
        if (strokes.isEmpty()) return null
        if (pageRect.width() <= 0f || pageRect.height() <= 0f) return null

        val source = inkBounds(strokes) ?: return null
        val left = max(source.left, pageRect.left)
        val top = max(source.top, pageRect.top)
        val right = min(source.right, pageRect.right)
        val bottom = min(source.bottom, pageRect.bottom)
        if (right - left <= 0f || bottom - top <= 0f) return null

        val outWidth = max(1, (right - left).roundToInt())
        val outHeight = max(1, (bottom - top).roundToInt())
        val bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(outWidth / (right - left), outHeight / (bottom - top))
        canvas.translate(-left, -top)
        drawStrokes(canvas, strokes)

        val pageHeight = 595f * pageHeightPt / pageWidthPt.coerceAtLeast(1f)
        val bounds = RectF(
            595f * (left - pageRect.left) / pageRect.width(),
            pageHeight * (top - pageRect.top) / pageRect.height(),
            595f * (right - pageRect.left) / pageRect.width(),
            pageHeight * (bottom - pageRect.top) / pageRect.height(),
        )
        return Overlay(bitmap, bounds)
    }

    private fun drawStrokes(canvas: Canvas, source: List<Stroke>) {
        source.forEach { stroke ->
            inkPaint.color = withAlpha(stroke.color)
            inkPaint.strokeWidth = stroke.width
            canvas.drawPath(stroke.path, inkPaint)
        }
    }

    private fun inkBounds(strokes: List<Stroke>): RectF? {
        if (strokes.isEmpty()) return null
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        strokes.forEach { stroke ->
            var index = 0
            while (index + 1 < stroke.points.size) {
                minX = min(minX, stroke.points[index])
                maxX = max(maxX, stroke.points[index])
                minY = min(minY, stroke.points[index + 1])
                maxY = max(maxY, stroke.points[index + 1])
                index += 2
            }
        }
        if (minX > maxX) return null
        return RectF(minX, minY, maxX, maxY)
    }

    private fun withAlpha(color: Int): Int {
        val alpha = (inkAlpha.coerceIn(0f, 1f) * 255f).toInt()
        return (alpha shl 24) or (color and 0x00FFFFFF)
    }

    private fun updatePageRect() {
        val bitmap = pageBitmap
        if (bitmap == null || width == 0 || height == 0) {
            pageRect.setEmpty()
            return
        }
        val scale = min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        pageRect.set(
            (width - drawWidth) / 2f,
            (height - drawHeight) / 2f,
            (width + drawWidth) / 2f,
            (height + drawHeight) / 2f,
        )
    }

    private fun dp(value: Float): Float = value * density
}
