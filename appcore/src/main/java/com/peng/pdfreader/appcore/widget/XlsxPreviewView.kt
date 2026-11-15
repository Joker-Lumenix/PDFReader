package com.peng.pdfreader.appcore.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.peng.pdfreader.appcore.R
import com.peng.pdfreader.appcore.office.OfficeColors
import com.peng.pdfreader.appcore.office.xlsx.XlsxCell
import com.peng.pdfreader.appcore.office.xlsx.XlsxSheet
import com.peng.pdfreader.appcore.office.xlsx.XlsxWorkbook
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders a parsed spreadsheet.
 *
 * The view paints the sheet tabs and the cell grid itself and scrolls both
 * axes by touch. The values are already formatted by the parser, the view only
 * applies the visual style of a cell (fill, borders, alignment, font).
 */
class XlsxPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private fun dp(value: Float): Float = value * density

    private val tabPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_secondary)
        textSize = dp(13f)
    }
    private val tabActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_primary)
        textSize = dp(13f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val tabIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.brand_primary)
    }
    private val tabBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.surface_card)
    }
    private val tabActiveBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.brand_primary_container)
    }
    private val gridBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.surface_muted)
    }
    private val headerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_secondary)
        textSize = dp(11f)
        textAlign = Paint.Align.CENTER
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = context.getColor(R.color.surface_border_strong)
        strokeWidth = density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cellTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_primary)
        textSize = dp(12f)
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = context.getColor(R.color.brand_primary)
        strokeWidth = 2f * density
    }

    private var scrollX = 0f
    private var scrollY = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var selectedCell: Pair<Int, Int>? = null

    /** The workbook that is rendered. */
    var workbook: XlsxWorkbook? = null
        set(value) {
            field = value
            sheetIndex = value?.activeSheetIndex ?: 0
            scrollX = 0f
            scrollY = 0f
            invalidate()
        }

    /** Index of the sheet that is rendered. */
    var sheetIndex: Int = 0
        set(value) {
            val count = workbook?.sheets?.size ?: 0
            val bounded = if (count == 0) 0 else value.coerceIn(0, count - 1)
            if (field != bounded) {
                field = bounded
                scrollX = 0f
                scrollY = 0f
                invalidate()
                onSheetChanged?.invoke(bounded)
            }
        }

    /** Raised with the index of the newly selected sheet. */
    var onSheetChanged: ((Int) -> Unit)? = null

    /** Raised with the row and the column of the tapped cell. */
    var onCellTapped: ((Int, Int) -> Unit)? = null

    private val tabStripHeight get() = dp(36f)
    private val headerHeight get() = dp(26f)

    init {
        isClickable = true
    }

    private fun currentSheet(): XlsxSheet? = workbook?.sheets?.getOrNull(sheetIndex)

    override fun onDraw(canvas: Canvas) {
        val sheet = currentSheet() ?: return
        drawTabs(canvas)
        drawGrid(canvas, sheet)
    }

    private fun drawTabs(canvas: Canvas) {
        val sheets = workbook?.sheets.orEmpty()
        canvas.drawRect(0f, 0f, width.toFloat(), tabStripHeight, tabBackgroundPaint)
        var x = dp(4f)
        sheets.forEachIndexed { index, sheet ->
            val label = sheet.name
            val paint = if (index == sheetIndex) tabActivePaint else tabPaint
            paint.textSize = dp(13f)
            val textWidth = paint.measureText(label)
            if (index == sheetIndex) {
                canvas.drawRect(x, dp(4f), x + textWidth + dp(16f), tabStripHeight - dp(2f), tabActiveBackgroundPaint)
                canvas.drawRect(x, tabStripHeight - dp(2f), x + textWidth + dp(16f), tabStripHeight, tabIndicatorPaint)
            }
            canvas.drawText(label, x + dp(8f), tabStripHeight - dp(10f), paint)
            x += textWidth + dp(24f)
        }
    }

    private fun drawGrid(canvas: Canvas, sheet: XlsxSheet) {
        val top = tabStripHeight
        val gridHeight = height - top
        if (gridHeight <= 0f) return

        canvas.drawRect(0f, top, width.toFloat(), height.toFloat(), gridBackgroundPaint)

        val columns = columnWidths(sheet)
        val rows = sheet.rows
        var rowTop = top + headerHeight - scrollY

        canvas.save()
        canvas.clipRect(0f, top + headerHeight, width.toFloat(), height.toFloat())

        rows.forEachIndexed { rowIndex, row ->
            val rowHeight = max(dp(18f), row.heightDp * density)
            if (rowTop + rowHeight > top + headerHeight && rowTop < height) {
                var columnLeft = -scrollX
                for (cell in row.cells) {
                    if (cell.isCovered) continue
                    val span = cell.columnSpan.coerceAtLeast(1)
                    val rowSpan = cell.rowSpan.coerceAtLeast(1)
                    var cellWidth = 0f
                    for (index in cell.column until min(cell.column + span, columns.size)) {
                        cellWidth += columns[index]
                    }
                    if (cellWidth <= 0f) cellWidth = dp(64f)
                    var cellHeight = rowHeight
                    for (offset in 1 until rowSpan) {
                        rows.getOrNull(rowIndex + offset)?.let {
                            cellHeight += max(dp(18f), it.heightDp * density)
                        }
                    }
                    val rect = RectF(columnLeft, rowTop, columnLeft + cellWidth, rowTop + cellHeight)
                    drawCell(canvas, cell, rect)
                    columnLeft += cellWidth
                }
            }
            rowTop += rowHeight
        }

        canvas.restore()

        // The column header stays pinned above the grid.
        var headerLeft = -scrollX
        canvas.drawRect(0f, top, width.toFloat(), top + headerHeight, headerPaint)
        columns.forEachIndexed { index, columnWidth ->
            val label = columnName(index)
            canvas.drawText(label, headerLeft + columnWidth / 2f, top + headerHeight - dp(8f), headerTextPaint)
            canvas.drawLine(headerLeft, top, headerLeft, top + headerHeight, borderPaint)
            headerLeft += columnWidth
        }
        canvas.drawLine(headerLeft, top, headerLeft, top + headerHeight, borderPaint)
        canvas.drawLine(0f, top + headerHeight, width.toFloat(), top + headerHeight, borderPaint)
    }

    private fun drawCell(canvas: Canvas, cell: XlsxCell, rect: RectF) {
        val style = cell.style
        style.background?.let {
            fillPaint.color = OfficeColors.toAndroid(it)
            canvas.drawRect(rect, fillPaint)
        }
        canvas.drawRect(rect, borderPaint)
        val selected = selectedCell?.first == cell.row && selectedCell?.second == cell.column
        if (selected) canvas.drawRect(rect, selectionPaint)

        val colour = style.fontColor?.let { OfficeColors.toAndroid(it) }
            ?: context.getColor(R.color.text_primary)
        cellTextPaint.color = colour
        cellTextPaint.textSize = dp(12f)
        cellTextPaint.typeface = Typeface.create(
            Typeface.DEFAULT,
            if (style.bold) Typeface.BOLD else Typeface.NORMAL,
        )
        cellTextPaint.textAlign = when (style.horizontal) {
            "center" -> Paint.Align.CENTER
            "right" -> Paint.Align.RIGHT
            else -> Paint.Align.LEFT
        }
        val padding = dp(4f)
        val textX = when (style.horizontal) {
            "center" -> rect.centerX()
            "right" -> rect.right - padding
            else -> rect.left + padding
        }
        val baseline = rect.centerY() - (cellTextPaint.descent() + cellTextPaint.ascent()) / 2f

        if (style.wrapText) {
            val builder = SpannableStringBuilder(cell.value)
            if (style.italic) {
                builder.setSpan(
                    StyleSpan(Typeface.ITALIC),
                    0,
                    builder.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            if (style.underline) {
                builder.setSpan(UnderlineSpan(), 0, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val layout = StaticLayout.Builder
                .obtain(builder, 0, builder.length, cellTextPaint, max(1, (rect.width() - padding * 2).roundToInt()))
                .setAlignment(alignmentOf(style.horizontal))
                .setIncludePad(false)
                .build()
            canvas.save()
            canvas.translate(rect.left + padding, rect.centerY() - layout.height / 2f)
            layout.draw(canvas)
            canvas.restore()
        } else {
            canvas.drawText(cell.value, textX, baseline, cellTextPaint)
        }
    }

    private fun alignmentOf(horizontal: String?): Layout.Alignment = when (horizontal) {
        "center" -> Layout.Alignment.ALIGN_CENTER
        "right" -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_NORMAL
    }

    private fun columnWidths(sheet: XlsxSheet): List<Float> {
        if (sheet.columnWidthsDp.isEmpty()) return listOf(dp(64f))
        return sheet.columnWidthsDp.map { max(dp(32f), it * density) }
    }

    private fun columnName(index: Int): String {
        var value = index
        val builder = StringBuilder()
        while (value >= 0) {
            builder.insert(0, ('A' + value % 26))
            value = value / 26 - 1
        }
        return builder.toString()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                dragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                if (!dragging && (Math.abs(event.x - downX) > dp(4f) || Math.abs(event.y - downY) > dp(4f))) {
                    dragging = true
                }
                if (dragging) {
                    scrollX = (scrollX - dx).coerceIn(0f, maxScrollX())
                    scrollY = (scrollY - dy).coerceIn(0f, maxScrollY())
                    invalidate()
                }
                lastX = event.x
                lastY = event.y
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!dragging) handleTap(event.x, event.y)
                dragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTap(x: Float, y: Float) {
        if (y <= tabStripHeight) {
            var tabLeft = dp(4f)
            val sheets = workbook?.sheets.orEmpty()
            sheets.forEachIndexed { index, sheet ->
                val textWidth = tabPaint.measureText(sheet.name)
                if (x in tabLeft..(tabLeft + textWidth + dp(16f))) {
                    sheetIndex = index
                    return
                }
                tabLeft += textWidth + dp(24f)
            }
            return
        }
        val sheet = currentSheet() ?: return
        val columns = columnWidths(sheet)
        var rowTop = tabStripHeight + headerHeight - scrollY
        for (row in sheet.rows) {
            val rowHeight = max(dp(18f), row.heightDp * density)
            if (y in rowTop..(rowTop + rowHeight)) {
                var columnLeft = -scrollX
                for (cell in row.cells) {
                    if (cell.isCovered) continue
                    val span = cell.columnSpan.coerceAtLeast(1)
                    var cellWidth = 0f
                    for (index in cell.column until min(cell.column + span, columns.size)) {
                        cellWidth += columns[index]
                    }
                    if (cellWidth <= 0f) cellWidth = dp(64f)
                    if (x in columnLeft..(columnLeft + cellWidth)) {
                        selectedCell = cell.row to cell.column
                        onCellTapped?.invoke(cell.row, cell.column)
                        invalidate()
                        return
                    }
                    columnLeft += cellWidth
                }
                return
            }
            rowTop += rowHeight
        }
    }

    private fun maxScrollX(): Float {
        val sheet = currentSheet() ?: return 0f
        val total = columnWidths(sheet).sum()
        return max(0f, total - width)
    }

    private fun maxScrollY(): Float {
        val sheet = currentSheet() ?: return 0f
        val total = sheet.rows.sumOf { max(dp(18f), it.heightDp * density).toDouble() }.toFloat()
        return max(0f, total - (height - tabStripHeight - headerHeight))
    }
}
