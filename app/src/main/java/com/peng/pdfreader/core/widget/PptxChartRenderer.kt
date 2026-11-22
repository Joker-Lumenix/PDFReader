package com.peng.pdfreader.core.widget

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.peng.pdfreader.core.office.OfficeColors
import com.peng.pdfreader.core.office.pptx.PptxChart
import com.peng.pdfreader.core.office.pptx.PptxChartSeries
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws a chart of a slide.
 *
 * The chart is drawn the way the office software draws it: a white area, a
 * thin grid, the bars or the lines of the rows in the colour of the row and
 * the names of the groups under the axis.
 */
object PptxChartRenderer {

    private const val TAG = "PptxChartRenderer"

    /** The colour of the text and of the lines. */
    private val TEXT_COLOUR = 0xFF404040.toInt()
    private val GRID_COLOUR = 0xFFE0E0E0.toInt()
    private val AXIS_COLOUR = 0xFFB0B0B0.toInt()
    private val BACKGROUND_COLOUR = 0xFFFFFFFF.toInt()

    /** The text of the chart and of the axis. */
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_COLOUR
        textSize = 10f
        typeface = Typeface.DEFAULT
    }

    /** The lines of the chart. */
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    /** The areas of the chart. */
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /**
     * Draws a chart into the given area.
     *
     * The area is in pixels, the scale is the pixels per deck dp.
     */
    fun draw(canvas: Canvas, chart: PptxChart, rect: RectF, scale: Float) {
        runCatching {
            drawChart(canvas, chart, rect, scale)
        }
    }

    private fun drawChart(canvas: Canvas, chart: PptxChart, rect: RectF, scale: Float) {
        if (rect.width() <= 0f || rect.height() <= 0f) return
        canvas.drawRect(rect, fillPaint.apply { color = BACKGROUND_COLOUR })

        val titleHeight = if (chart.title != null) 20f * scale else 0f
        if (chart.title != null) {
            textPaint.textSize = 11f * scale
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(chart.title.orEmpty(), rect.centerX(), rect.top + 13f * scale, textPaint)
        }
        textPaint.textAlign = Paint.Align.LEFT

        val legendWidth = if (chart.legend && chart.series.size > 1) {
            min(rect.width() * 0.3f, 90f * scale)
        } else {
            0f
        }
        val plot = RectF(
            rect.left + 34f * scale,
            rect.top + titleHeight + 6f * scale,
            rect.right - legendWidth - 6f * scale,
            rect.bottom - 22f * scale,
        )
        if (plot.width() <= 10f || plot.height() <= 10f) return

        when (chart.kind) {
            "pie", "doughnut" -> drawPie(canvas, chart, plot, scale)
            "line", "radar", "scatter" -> drawLines(canvas, chart, plot, scale)
            "area" -> drawAreas(canvas, chart, plot, scale)
            else -> drawBars(canvas, chart, plot, scale)
        }
        if (legendWidth > 0f) drawLegend(canvas, chart, rect, plot, legendWidth, scale)
    }

    // ------------------------------------------------------------------ bars

    /** Draws the bars of a column or of a row chart. */
    private fun drawBars(canvas: Canvas, chart: PptxChart, plot: RectF, scale: Float) {
        val categories = chart.categories
        val values = chart.series.flatMap { it.values }.filterNotNull()
        val maxValue = chart.maxValue ?: values.maxOrNull() ?: 1f
        val minValue = chart.minValue ?: values.minOrNull() ?: 0f
        val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f
        val horizontal = chart.kind == "bar"

        if (chart.showValueAxis) {
            drawValueAxis(canvas, chart, plot, minValue, maxValue, horizontal, scale)
        }

        val count = max(categories.size, chart.series.maxOf { it.values.size })
        if (count <= 0) return
        val slot = if (horizontal) plot.height() / count else plot.width() / count
        val stacked = chart.grouping == "stacked" || chart.grouping == "percentStacked"
        val groupWidth = slot * 0.7f
        val barWidth = if (stacked) groupWidth else groupWidth / max(chart.series.size, 1)

        for (category in 0 until count) {
            val offset = if (horizontal) {
                plot.top + category * slot + (slot - groupWidth) / 2f
            } else {
                plot.left + category * slot + (slot - groupWidth) / 2f
            }
            var stack = 0f
            chart.series.forEachIndexed { seriesIndex, series ->
                val value = series.values.getOrNull(category) ?: return@forEachIndexed
                val size = if (stacked) value else value
                val length = (size / range) * if (horizontal) plot.width() else plot.height()
                val colour = series.color?.let { OfficeColors.toAndroid(it) } ?: 0xFF4472C4.toInt()
                fillPaint.color = colour
                if (horizontal) {
                    val top = offset + seriesIndex * barWidth
                    canvas.drawRect(plot.left, top, plot.left + length, top + barWidth, fillPaint)
                    if (stacked) {
                        // The rows of a stacked chart stand on each other.
                    }
                } else {
                    val left = offset + seriesIndex * barWidth
                    canvas.drawRect(left, plot.bottom - length, left + barWidth, plot.bottom, fillPaint)
                }
                stack += size
            }
            // The name of the group stands under or beside the bars.
            textPaint.textSize = 9f * scale
            val label = categories.getOrNull(category) ?: ""
            if (horizontal) {
                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(label, plot.left - 4f * scale, offset + groupWidth / 2f + 3f * scale, textPaint)
            } else {
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(label, offset + groupWidth / 2f, plot.bottom + 12f * scale, textPaint)
            }
        }
        textPaint.textAlign = Paint.Align.LEFT
    }

    /** Draws the axis of the values. */
    private fun drawValueAxis(
        canvas: Canvas,
        chart: PptxChart,
        plot: RectF,
        minValue: Float,
        maxValue: Float,
        horizontal: Boolean,
        scale: Float,
    ) {
        linePaint.color = AXIS_COLOUR
        linePaint.strokeWidth = max(1f, scale)
        if (horizontal) {
            canvas.drawLine(plot.left, plot.top, plot.left, plot.bottom, linePaint)
        } else {
            canvas.drawLine(plot.left, plot.top, plot.left, plot.bottom, linePaint)
            canvas.drawLine(plot.left, plot.bottom, plot.right, plot.bottom, linePaint)
        }
        linePaint.color = GRID_COLOUR
        textPaint.textSize = 8f * scale
        val steps = 4
        for (step in 0..steps) {
            val fraction = step / steps.toFloat()
            val value = minValue + (maxValue - minValue) * fraction
            if (horizontal) {
                val x = plot.left + plot.width() * fraction
                canvas.drawLine(x, plot.top, x, plot.bottom, linePaint)
                textPaint.textAlign = Paint.Align.CENTER
                canvas.drawText(formatValue(value), x, plot.bottom + 12f * scale, textPaint)
            } else {
                val y = plot.bottom - plot.height() * fraction
                canvas.drawLine(plot.left, y, plot.right, y, linePaint)
                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(formatValue(value), plot.left - 3f * scale, y + 3f * scale, textPaint)
            }
        }
        textPaint.textAlign = Paint.Align.LEFT
    }

    // ----------------------------------------------------------------- lines

    /** Draws the lines of a line, of a radar or of a scatter chart. */
    private fun drawLines(canvas: Canvas, chart: PptxChart, plot: RectF, scale: Float) {
        val values = chart.series.flatMap { it.values }.filterNotNull()
        val maxValue = chart.maxValue ?: values.maxOrNull() ?: 1f
        val minValue = chart.minValue ?: values.minOrNull() ?: 0f
        val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f
        if (chart.showValueAxis) {
            drawValueAxis(canvas, chart, plot, minValue, maxValue, false, scale)
        }
        val count = max(chart.categories.size, chart.series.maxOf { it.values.size })
        if (count <= 0) return
        val step = if (count > 1) plot.width() / (count - 1) else 0f

        chart.series.forEach { series ->
            val colour = series.color?.let { OfficeColors.toAndroid(it) } ?: 0xFF4472C4.toInt()
            val path = Path()
            var started = false
            series.values.forEachIndexed { index, value ->
                if (value == null) {
                    started = false
                    return@forEachIndexed
                }
                val x = plot.left + step * index
                val y = plot.bottom - ((value - minValue) / range) * plot.height()
                if (started) path.lineTo(x, y) else path.moveTo(x, y)
                started = true
            }
            linePaint.color = colour
            linePaint.strokeWidth = max(1.5f, 1.5f * scale)
            canvas.drawPath(path, linePaint)
            // A dot marks every value of the row.
            fillPaint.color = colour
            series.values.forEachIndexed { index, value ->
                if (value == null) return@forEachIndexed
                val x = plot.left + step * index
                val y = plot.bottom - ((value - minValue) / range) * plot.height()
                canvas.drawCircle(x, y, max(1.5f, 1.5f * scale), fillPaint)
            }
        }
        // The names of the groups stand under the axis.
        textPaint.textSize = 9f * scale
        textPaint.textAlign = Paint.Align.CENTER
        chart.categories.forEachIndexed { index, label ->
            if (label.isBlank()) return@forEachIndexed
            val x = plot.left + step * index
            canvas.drawText(label, x, plot.bottom + 12f * scale, textPaint)
        }
        textPaint.textAlign = Paint.Align.LEFT
    }

    // ----------------------------------------------------------------- areas

    /** Draws the areas of an area chart. */
    private fun drawAreas(canvas: Canvas, chart: PptxChart, plot: RectF, scale: Float) {
        val values = chart.series.flatMap { it.values }.filterNotNull()
        val maxValue = chart.maxValue ?: values.maxOrNull() ?: 1f
        val minValue = chart.minValue ?: values.minOrNull() ?: 0f
        val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f
        if (chart.showValueAxis) {
            drawValueAxis(canvas, chart, plot, minValue, maxValue, false, scale)
        }
        val count = max(chart.categories.size, chart.series.maxOf { it.values.size })
        if (count <= 0) return
        val step = if (count > 1) plot.width() / (count - 1) else 0f
        chart.series.forEach { series ->
            val colour = series.color?.let { OfficeColors.toAndroid(it) } ?: 0xFF4472C4.toInt()
            val path = Path()
            var started = false
            series.values.forEachIndexed { index, value ->
                if (value == null) {
                    started = false
                    return@forEachIndexed
                }
                val x = plot.left + step * index
                val y = plot.bottom - ((value - minValue) / range) * plot.height()
                if (started) path.lineTo(x, y) else path.moveTo(x, y)
                started = true
            }
            path.lineTo(plot.left + step * (series.values.size - 1).coerceAtLeast(0), plot.bottom)
            path.lineTo(plot.left, plot.bottom)
            path.close()
            fillPaint.color = colour and 0x66FFFFFF
            canvas.drawPath(path, fillPaint)
        }
    }

    // ------------------------------------------------------------------ pie

    /** Draws the slices of a pie or of a doughnut chart. */
    private fun drawPie(canvas: Canvas, chart: PptxChart, plot: RectF, scale: Float) {
        val series = chart.series.firstOrNull() ?: return
        val values = series.values.map { it ?: 0f }
        val total = values.sum()
        if (total <= 0f) return
        val radius = min(plot.width(), plot.height()) / 2f
        val centreX = plot.centerX()
        val centreY = plot.centerY()
        val area = RectF(
            centreX - radius,
            centreY - radius,
            centreX + radius,
            centreY + radius,
        )
        val hole = if (chart.kind == "doughnut") radius * 0.5f else 0f
        var start = -90f
        values.forEachIndexed { index, value ->
            val sweep = 360f * (value / total)
            val colour = colourOf(index, chart)
            fillPaint.color = colour
            if (hole > 0f) {
                val inner = RectF(
                    centreX - radius + hole,
                    centreY - radius + hole,
                    centreX + radius - hole,
                    centreY + radius - hole,
                )
                canvas.drawArc(area, start, sweep, true, fillPaint)
                // The hole is drawn in the colour of the background.
                fillPaint.color = BACKGROUND_COLOUR
                canvas.drawArc(inner, start, sweep, true, fillPaint)
            } else {
                canvas.drawArc(area, start, sweep, true, fillPaint)
            }
            // The name of the group stands beside its slice.
            val middle = Math.toRadians((start + sweep / 2f).toDouble())
            val labelRadius = radius * 0.75f
            textPaint.textSize = 9f * scale
            textPaint.textAlign = Paint.Align.CENTER
            val x = centreX + cos(middle).toFloat() * labelRadius
            val y = centreY + sin(middle).toFloat() * labelRadius
            canvas.drawText(
                chart.categories.getOrNull(index).orEmpty(),
                x,
                y,
                textPaint.apply { color = 0xFFFFFFFF.toInt() },
            )
            textPaint.color = TEXT_COLOUR
            start += sweep
        }
        textPaint.textAlign = Paint.Align.LEFT
    }

    // --------------------------------------------------------------- legend

    /** Draws the names of the rows beside the chart. */
    private fun drawLegend(
        canvas: Canvas,
        chart: PptxChart,
        rect: RectF,
        plot: RectF,
        width: Float,
        scale: Float,
    ) {
        val left = plot.right + 6f * scale
        var y = plot.top + 6f * scale
        textPaint.textSize = 9f * scale
        chart.series.forEachIndexed { index, series ->
            val name = series.name.ifBlank { chart.categories.getOrNull(index).orEmpty() }
            if (name.isBlank()) return@forEachIndexed
            fillPaint.color = colourOf(index, chart)
            canvas.drawRect(left, y - 6f * scale, left + 8f * scale, y + 2f * scale, fillPaint)
            canvas.drawText(name, left + 12f * scale, y, textPaint)
            y += 14f * scale
        }
    }

    /** The colour of a row of the chart. */
    private fun colourOf(index: Int, chart: PptxChart): Int {
        val series = chart.series.getOrNull(index)
        return series?.color?.let { OfficeColors.toAndroid(it) } ?: 0xFF4472C4.toInt()
    }

    /** A value of the axis, without the noise of the floating point. */
    private fun formatValue(value: Float): String {
        if (abs(value) >= 1000f) return value.toInt().toString()
        return if (value == value.toInt().toFloat()) value.toInt().toString() else "%.1f".format(value)
    }
}
