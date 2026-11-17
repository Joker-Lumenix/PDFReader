package com.peng.pdfreader.core.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * A single ink stroke in normalised (0..1) coordinates.
 *
 * The editors exchange strokes with the host page through this value type, so
 * a signature survives a round trip through the repository without keeping a
 * bitmap around.
 */
class InkStroke(
    val color: Int,
    /** Stroke width, normalised against the canvas width. */
    val width: Float,
    /** x / y pairs, both in the 0..1 range. */
    val points: FloatArray,
) {
    val isEmpty: Boolean get() = points.size < 4

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InkStroke) return false
        return color == other.color &&
            width == other.width &&
            points.contentEquals(other.points)
    }

    override fun hashCode(): Int {
        var result = color
        result = 31 * result + width.hashCode()
        result = 31 * result + points.contentHashCode()
        return result
    }
}

/**
 * Canvas of the signature editor.
 *
 * The view collects the strokes of the signature, supports undo / redo, a
 * colour and a stroke width and can export the ink as a transparent bitmap.
 * When nothing has been drawn it paints the "sign here" placeholder and the
 * baseline of the design.
 */
class SignatureCanvasView @JvmOverloads constructor(
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

    private val density = resources.displayMetrics.density

    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#d6d7db")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#c4c4c4")
        strokeWidth = density
    }

    private val strokes = ArrayList<Stroke>()
    private val redoStack = ArrayList<Stroke>()
    private val activePoints = ArrayList<Float>()
    private var active: Path? = null
    private var lastX = 0f
    private var lastY = 0f

    /** Colour of the next stroke. */
    var strokeColor: Int = Color.BLACK
        set(value) {
            field = value
        }

    /** Width of the next stroke, in pixels. */
    var strokeWidth: Float = dp(3f)
        set(value) {
            field = value.coerceAtLeast(1f)
        }

    /** Placeholder shown while the canvas is empty. */
    var placeholder: CharSequence? = null

    /** Size of the placeholder text, in pixels. */
    var placeholderTextSize: Float = sp(30f)
        set(value) {
            field = value
            placeholderPaint.textSize = value
        }

    /** Whether the baseline under the placeholder is painted. */
    var showBaseline: Boolean = true

    /** Vertical position of the baseline, as a ratio of the view height. */
    var baselineRatio: Float = 0.473f

    /** Raised whenever the ink changes. */
    var onStrokesChanged: (() -> Unit)? = null

    val hasInk: Boolean get() = strokes.isNotEmpty()
    val canUndo: Boolean get() = strokes.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    init {
        isClickable = true
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
                    strokes += Stroke(path, activePoints.toFloatArray(), strokeColor, strokeWidth)
                    redoStack.clear()
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
        drawPlaceholder(canvas)
        drawStrokes(canvas, strokes)
        active?.let { path ->
            inkPaint.color = strokeColor
            inkPaint.strokeWidth = strokeWidth
            canvas.drawPath(path, inkPaint)
        }
    }

    /** Removes the last stroke. */
    fun undo() {
        val stroke = strokes.removeLastOrNull() ?: return
        redoStack.add(stroke)
        onStrokesChanged?.invoke()
        invalidate()
    }

    /** Restores the last undone stroke. */
    fun redo() {
        val stroke = redoStack.removeLastOrNull() ?: return
        strokes.add(stroke)
        onStrokesChanged?.invoke()
        invalidate()
    }

    /** Drops every stroke. */
    fun clear() {
        strokes.clear()
        redoStack.clear()
        onStrokesChanged?.invoke()
        invalidate()
    }

    /** The strokes of the signature, normalised against the canvas size. */
    fun inkStrokes(): List<InkStroke> {
        val canvasWidth = width.toFloat().coerceAtLeast(1f)
        val canvasHeight = height.toFloat().coerceAtLeast(1f)
        return strokes.map { stroke ->
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

    /** Bounding box of the ink, normalised against the canvas size. */
    fun inkBounds(): RectF {
        if (strokes.isEmpty()) return RectF()
        val canvasWidth = width.toFloat().coerceAtLeast(1f)
        val canvasHeight = height.toFloat().coerceAtLeast(1f)
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
        if (minX > maxX) return RectF()
        return RectF(minX / canvasWidth, minY / canvasHeight, maxX / canvasWidth, maxY / canvasHeight)
    }

    /**
     * Renders the ink on a transparent bitmap.
     *
     * @param outWidth width of the bitmap, defaults to the view width
     * @param outHeight height of the bitmap, defaults to the view height
     */
    fun exportBitmap(outWidth: Int = width, outHeight: Int = height): Bitmap? {
        if (strokes.isEmpty()) return null
        val targetWidth = outWidth.coerceAtLeast(1)
        val targetHeight = outHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (width > 0 && height > 0) {
            canvas.scale(targetWidth / width.toFloat(), targetHeight / height.toFloat())
        }
        drawStrokes(canvas, strokes)
        return bitmap
    }

    private fun drawPlaceholder(canvas: Canvas) {
        if (strokes.isNotEmpty()) return
        val text = placeholder ?: return
        val baseline = height * baselineRatio
        if (showBaseline) {
            canvas.drawLine(dp(25f), baseline, width - dp(24f), baseline, baselinePaint)
        }
        placeholderPaint.textSize = placeholderTextSize
        canvas.drawText(text.toString(), width / 2f, baseline - dp(19f), placeholderPaint)
    }

    private fun drawStrokes(canvas: Canvas, source: List<Stroke>) {
        source.forEach { stroke ->
            inkPaint.color = stroke.color
            inkPaint.strokeWidth = stroke.width
            canvas.drawPath(stroke.path, inkPaint)
        }
    }

    private fun dp(value: Float): Float = value * density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
