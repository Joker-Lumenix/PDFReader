package com.peng.pdfreader.core.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.peng.pdfreader.core.widget.InkStroke
import kotlin.math.max
import kotlin.math.min

/**
 * Renders the ink of a page on a transparent picture.
 *
 * The picture and the area it covers are what `PdfEngine.flattenOverlay`
 * expects: the picture holds exactly the part of the page the ink lives on
 * and the bounds tell where that part sits on the page. The coordinate space
 * of the bounds is the one of the annotation editor - a page of 595 points
 * wide, with the origin in the top left corner.
 */
object PageInk {

    /** The picture of the ink and the area it covers. */
    data class Overlay(val bitmap: Bitmap, val bounds: RectF)

    /** The width of the page the overlay is built for, in points. */
    private const val PAGE_WIDTH_PT = 595f

    /** How many pixels a point of the page gets. */
    private const val RESOLUTION = 2f

    /**
     * Builds the picture of the given strokes.
     *
     * @param strokes the ink of the page, expressed as fractions of the page
     * @param pageWidthPt the width of the page, in points
     * @param pageHeightPt the height of the page, in points
     */
    fun overlayOf(
        strokes: List<InkStroke>,
        pageWidthPt: Int,
        pageHeightPt: Int,
    ): Overlay? {
        val used = strokes.filter { !it.isEmpty }
        if (used.isEmpty()) return null
        val aspect = if (pageWidthPt > 0) {
            pageHeightPt.toFloat() / pageWidthPt.toFloat()
        } else {
            1.414f
        }
        val pageHeight = PAGE_WIDTH_PT * aspect

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        used.forEach { stroke ->
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

        // The stroke has a width, so the area of the ink is a bit bigger than
        // the points of the strokes.
        val margin = used.maxOf { it.width } * PAGE_WIDTH_PT / 2f
        val bounds = RectF(
            (minX * PAGE_WIDTH_PT - margin).coerceAtLeast(0f),
            (minY * pageHeight - margin).coerceAtLeast(0f),
            (maxX * PAGE_WIDTH_PT + margin).coerceAtMost(PAGE_WIDTH_PT),
            (maxY * pageHeight + margin).coerceAtMost(pageHeight),
        )
        if (bounds.width() <= 0f || bounds.height() <= 0f) return null

        val width = max(1, (bounds.width() * RESOLUTION).toInt())
        val height = max(1, (bounds.height() * RESOLUTION).toInt())
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // The strokes are drawn in the space of the page, the canvas puts them
        // on the picture of the overlay.
        canvas.scale(RESOLUTION, RESOLUTION)
        canvas.translate(-bounds.left, -bounds.top)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        used.forEach { stroke ->
            paint.color = stroke.color
            paint.strokeWidth = (stroke.width * PAGE_WIDTH_PT).coerceAtLeast(0.5f)
            val path = Path()
            var first = true
            var index = 0
            while (index + 1 < stroke.points.size) {
                val x = stroke.points[index] * PAGE_WIDTH_PT
                val y = stroke.points[index + 1] * pageHeight
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                index += 2
            }
            canvas.drawPath(path, paint)
        }
        return Overlay(bitmap, bounds)
    }
}
