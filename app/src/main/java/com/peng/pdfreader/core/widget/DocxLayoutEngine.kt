package com.peng.pdfreader.core.widget

import android.content.Context
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import com.peng.pdfreader.R
import com.peng.pdfreader.core.office.OfficeColors
import com.peng.pdfreader.core.office.docx.DocxBlock
import com.peng.pdfreader.core.office.docx.DocxCell
import com.peng.pdfreader.core.office.docx.DocxDocument
import com.peng.pdfreader.core.util.Logger
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lays a Word document out into the pages of the document.
 *
 * The engine measures the blocks of the document and splits them into pages,
 * the way Word does it: a paragraph that does not fit is broken at a line
 * boundary and a table is broken at a row boundary.
 *
 * The measurement is expensive - every paragraph needs a static layout - so
 * the pages are built one after the other and only for the pages the reader
 * shows. The engine is therefore driven from a background thread, the reader
 * only reads the pages that are ready.
 */
internal class DocxLayoutEngine(
    private val context: Context,
    private val document: DocxDocument,
) {

    /** One page of the document. */
    class Page(val index: Int, val items: List<Item>)

    /** One item of a page. */
    sealed interface Item {

        /**
         * The top of the item, relative to the top left corner of the page.
         */
        val top: Float

        class Paragraph(
            override val top: Float,
            val left: Float,
            val text: StaticLayout,
            val firstLine: Int,
            val lastLine: Int,
            val bullet: String?,
            val bulletLeft: Float,
            /**
             * The number of the block of the document the paragraph belongs to.
             *
             * The reader needs it to know which paragraph of the document the
             * user touches.
             */
            val blockIndex: Int,
        ) : Item

        class Picture(
            override val top: Float,
            val rect: RectF,
            val bytes: ByteArray,
        ) : Item

        class Table(
            override val top: Float,
            val left: Float,
            val rows: List<Row>,
        ) : Item
    }

    /** One row of a table, its top is relative to the table. */
    class Row(val top: Float, val height: Float, val cells: List<Cell>)

    /** One cell of a row of a table. */
    class Cell(
        val left: Float,
        val width: Float,
        val background: Int?,
        val layouts: List<StaticLayout>,
        val vertical: String,
    )

    /**
     * A cell of a row of a table, with the height it needs.
     *
     * The height is only known after the text of the cell is measured.
     */
    private class MeasuredCell(
        val left: Float,
        val width: Float,
        val background: Int?,
        val layouts: List<StaticLayout>,
        val vertical: String,
        val height: Float,
    ) {
        /** The cell of the page that is drawn. */
        fun toCell(): Cell = Cell(left, width, background, layouts, vertical)
    }

    /** The part of a block that did not fit on the page before. */
    private sealed interface Carry {

        class ParagraphPart(
            val block: DocxBlock.Paragraph,
            val layout: StaticLayout,
            val firstLine: Int,
            val left: Float,
            val bullet: String?,
            val bulletLeft: Float,
            val spaceAfterPt: Float,
            val blockIndex: Int,
        ) : Carry

        class TablePart(
            /**
             * The rows of the table that are not placed yet.
             *
             * The rows keep their measurements, so a table that is broken
             * over two pages is not measured twice.
             */
            val rows: TableRows,
            val spaceAfterPt: Float,
            /**
             * The number of the table in the document.
             *
             * The engine needs it to go on with the document when the table
             * is completely placed.
             */
            val blockIndex: Int,
        ) : Carry
    }

    private val lock = Any()
    private val density = context.resources.displayMetrics.density
    private val textColour = context.getColor(R.color.text_primary)

    /** The width of the viewport the pages are laid out for. */
    private var viewportWidth = 0

    /** The pixels per point of the document. */
    private var scale = 1f

    /** The width of a page in pixels. */
    var pageWidth = 0
        private set

    /** The height of a page in pixels. */
    var pageHeight = 0
        private set

    /** The margins of the page in pixels. */
    private var marginLeft = 0f
    private var marginRight = 0f
    private var marginTop = 0f

    /** The width of the text area of a page in pixels. */
    private var textWidth = 0f

    /** The height of the text area of a page in pixels. */
    private var textHeight = 0f

    private val pages = ArrayList<Page>()
    private var blockIndex = 0
    private var carry: Carry? = null

    /**
     * The pages that are ready.
     *
     * The reader reads them without a lock, so a page that is built on a
     * background thread never blocks the interface.
     */
    @Volatile
    private var snapshot: List<Page> = emptyList()

    /** The number of the pages that are ready. */
    @Volatile
    var pageCount: Int = 0
        private set

    /** Whether the document is laid out completely. */
    @Volatile
    var isComplete: Boolean = false
        private set

    /**
     * Prepares the engine for the given width.
     *
     * The pages are dropped, they are built again for the new width.
     */
    fun resize(width: Int) {
        synchronized(lock) {
            if (width <= 0) return
            if (width == viewportWidth && pages.isNotEmpty()) return
            viewportWidth = width
            scale = width / document.pageWidthPt.coerceAtLeast(1f)
            pageWidth = width
            pageHeight = ceil(document.pageHeightPt * scale).toInt()
            marginLeft = document.marginLeftPt * scale
            marginRight = document.marginRightPt * scale
            marginTop = document.marginTopPt * scale
            textWidth = (pageWidth - marginLeft - marginRight).coerceAtLeast(dp(80f))
            textHeight = (pageHeight - marginTop - document.marginBottomPt * scale)
                .coerceAtLeast(dp(100f))
            pages.clear()
            blockIndex = 0
            carry = null
            publish()
        }
    }

    /**
     * Builds the pages until the given number of pages is ready.
     *
     * The call blocks, it therefore belongs on a background thread.
     */
    fun buildUntil(count: Int) {
        synchronized(lock) {
            val started = System.currentTimeMillis()
            while (pages.size < count && hasMore()) {
                pages += buildPage()
            }
            publish()
            Logger.d(
                TAG,
                "built ${pages.size} pages in ${System.currentTimeMillis() - started} ms, " +
                    "page ${pageWidth}x$pageHeight, text ${textWidth.toInt()}x${textHeight.toInt()}",
            )
        }
    }

    /** The page with the given index, null when it is not built yet. */
    fun page(index: Int): Page? = snapshot.getOrNull(index)

    /** Publishes the pages the reader can see. */
    private fun publish() {
        snapshot = ArrayList(pages)
        pageCount = pages.size
        isComplete = carry == null && blockIndex >= document.blocks.size
    }

    private fun hasMore(): Boolean = carry != null || blockIndex < document.blocks.size

    // ------------------------------------------------------------------ pages

    private fun buildPage(): Page {
        val items = ArrayList<Item>()
        var y = 0f

        /**
         * The rows of the table that is laid out at the moment.
         *
         * The rows are measured when the page needs them, so a table with
         * thousands of rows does not hold the reader back.
         */
        var tableRows: TableRows? = null

        val pending = carry
        if (pending != null) {
            when (pending) {
                is Carry.ParagraphPart -> {
                    y += placeParagraphPart(items, pending, y)
                    if (carry != null) return Page(pages.size, items)
                }

                is Carry.TablePart -> {
                    y += placeTablePart(items, pending, y)
                    if (carry != null) return Page(pages.size, items)
                }
            }
        }
        while (blockIndex < document.blocks.size) {
            val block = document.blocks[blockIndex]
            when (block) {
                is DocxBlock.Paragraph -> {
                    val space = block.spaceBeforePt * scale
                    val indent = block.indentLevel * dp(16f)
                    val bullet = block.listLabel?.takeIf { it.isNotBlank() }
                    val bulletWidth = if (bullet == null) 0f else dp(24f)
                    val width = (textWidth - indent - bulletWidth).toInt().coerceAtLeast(dp(80f).toInt())
                    val layout = layoutOf(block, width, baseSizeFor(block))
                    val left = marginLeft + indent + bulletWidth
                    val bulletLeft = marginLeft + indent

                    val top = y + space
                    if (top + layout.height <= textHeight) {
                        // The paragraph fits on the page.
                        items += Item.Paragraph(
                            top = marginTop + top,
                            left = left,
                            text = layout,
                            firstLine = 0,
                            lastLine = layout.lineCount,
                            bullet = bullet,
                            bulletLeft = bulletLeft,
                            blockIndex = blockIndex,
                        )
                        y = top + layout.height + block.spaceAfterPt * scale
                        blockIndex++
                        continue
                    }

                    // The paragraph is broken at a line boundary.
                    val available = textHeight - top
                    val lastLine = lineAt(layout, available, 0)
                    if (lastLine <= 0) {
                        if (y > 0f) break
                        // Even a single line does not fit: the paragraph is
                        // broken at the end of the page.
                        val forced = lineAt(layout, textHeight, 0)
                            .coerceAtLeast(1)
                            .coerceAtMost(layout.lineCount)
                        items += Item.Paragraph(
                            top = marginTop + top,
                            left = left,
                            text = layout,
                            firstLine = 0,
                            lastLine = forced,
                            bullet = bullet,
                            bulletLeft = bulletLeft,
                            blockIndex = blockIndex,
                        )
                        carry = Carry.ParagraphPart(
                            block,
                            layout,
                            forced,
                            left,
                            bullet,
                            bulletLeft,
                            block.spaceAfterPt,
                            blockIndex,
                        )
                        y = textHeight
                        break
                    }
                    items += Item.Paragraph(
                        top = marginTop + top,
                        left = left,
                        text = layout,
                        firstLine = 0,
                        lastLine = lastLine,
                        bullet = bullet,
                        bulletLeft = bulletLeft,
                        blockIndex = blockIndex,
                    )
                    carry = Carry.ParagraphPart(
                        block,
                        layout,
                        lastLine,
                        left,
                        bullet,
                        bulletLeft,
                        block.spaceAfterPt,
                        blockIndex,
                    )
                    y = textHeight
                    break
                }

                is DocxBlock.Table -> {
                    // The rows of a table are measured when the page needs
                    // them, a big table has thousands of rows and the reader
                    // would wait a long time for the first one.
                    val rows = tableRows ?: TableRows(block, widthsOf(block)).also { tableRows = it }
                    val available = textHeight - y
                    val placed = rows.take(available)
                    if (placed.isEmpty()) {
                        // A table without rows has no content, it is skipped.
                        tableRows = null
                        blockIndex++
                        continue
                    }
                    items += Item.Table(marginTop + y, marginLeft, placed)
                    y += placed.sumOf { it.height.toDouble() }.toFloat()
                    if (rows.hasMore) {
                        carry = Carry.TablePart(rows, 0f, blockIndex)
                        break
                    }
                    tableRows = null
                    blockIndex++
                }

                is DocxBlock.Image -> {
                    val width = (block.widthPt * scale).takeIf { it > 0f } ?: textWidth
                    val height = (block.heightPt * scale).takeIf { it > 0f } ?: textWidth
                    val top = y
                    if (top + height > textHeight) {
                        if (y > 0f) break
                        items += Item.Picture(
                            marginTop + top,
                            RectF(
                                marginLeft,
                                marginTop + top,
                                marginLeft + width,
                                marginTop + top + height,
                            ),
                            block.bytes,
                        )
                        y = textHeight
                        break
                    }
                    val left = when (block.alignment) {
                        "center" -> marginLeft + (textWidth - width) / 2f
                        "right" -> marginLeft + textWidth - width
                        else -> marginLeft
                    }
                    items += Item.Picture(
                        marginTop + top,
                        RectF(left, marginTop + top, left + width, marginTop + top + height),
                        block.bytes,
                    )
                    y = top + height
                    blockIndex++
                }
            }
        }

        return Page(pages.size, items)
    }

    /**
     * Places the rest of a paragraph on the page.
     *
     * @return the height the paragraph takes on the page
     */
    private fun placeParagraphPart(items: ArrayList<Item>, part: Carry.ParagraphPart, y: Float): Float {
        val layout = part.layout
        val start = part.firstLine.coerceIn(0, layout.lineCount)
        if (start >= layout.lineCount) {
            // The paragraph is completely placed.
            carry = null
            return part.spaceAfterPt * scale
        }
        val available = textHeight - y
        var lastLine = lineAt(layout, available, start)
        if (lastLine <= start) {
            if (y > 0f) return 0f
            // The page is empty and the line does not fit: it is placed
            // anyway, the paragraph is never lost.
            lastLine = (start + 1).coerceAtMost(layout.lineCount)
        }
        items += Item.Paragraph(
            top = marginTop + y,
            left = part.left,
            text = layout,
            firstLine = start,
            lastLine = lastLine,
            // The bullet belongs to the first part of the paragraph.
            bullet = null,
            bulletLeft = part.bulletLeft,
            blockIndex = part.blockIndex,
        )
        val used = heightOf(layout, start, lastLine)
        if (lastLine >= layout.lineCount) {
            carry = null
            // The paragraph is completely placed, the engine goes on with the
            // next block of the document.
            blockIndex = part.blockIndex + 1
            return used + part.spaceAfterPt * scale
        }
        carry = Carry.ParagraphPart(
            part.block,
            layout,
            lastLine,
            part.left,
            part.bullet,
            part.bulletLeft,
            part.spaceAfterPt,
            part.blockIndex,
        )
        return textHeight - y
    }

    /**
     * Places the rest of a table on the page.
     *
     * @return the height the table takes on the page
     */
    private fun placeTablePart(items: ArrayList<Item>, part: Carry.TablePart, y: Float): Float {
        val available = textHeight - y
        val placed = part.rows.take(available)
        if (placed.isEmpty()) {
            // A table without rows has no content, it is skipped.
            carry = null
            blockIndex = part.blockIndex + 1
            return 0f
        }
        items += Item.Table(marginTop + y, marginLeft, placed)
        val used = placed.sumOf { it.height.toDouble() }.toFloat()
        if (part.rows.hasMore) {
            carry = Carry.TablePart(part.rows, part.spaceAfterPt, part.blockIndex)
            return used
        }
        carry = null
        // The table is completely placed, the engine goes on with the next
        // block of the document.
        blockIndex = part.blockIndex + 1
        return used + part.spaceAfterPt * scale
    }

    /**
     * The rows of a table that fit into the given height.
     *
     * The rows keep their position relative to the table, so the part of a
     * table that moved to the next page is drawn at the right place.
     */
    private fun splitRows(rows: List<Row>, available: Float): List<Row> {
        if (rows.isEmpty()) return emptyList()
        var used = 0f
        val result = ArrayList<Row>()
        for (row in rows) {
            if (used + row.height > available && result.isNotEmpty()) break
            result += row
            used += row.height
        }
        // The rows start at the top of the table again, the part of a table
        // that moved to the next page is drawn at the right place.
        val offset = result.first().top
        return result.map { Row(it.top - offset, it.height, it.cells) }
    }

    // ----------------------------------------------------------------- tables

    /**
     * The rows of a table.
     *
     * A big table has thousands of rows and every cell of it needs a
     * measurement of its own. The rows are therefore measured one after the
     * other: the reader only waits for the rows that fit onto the page it
     * shows.
     */
    private inner class TableRows(
        private val table: DocxBlock.Table,
        private val widths: List<Float>,
    ) {

        /** The rows that were measured and are not placed yet. */
        private val measured = ArrayDeque<Row>()

        /** The number of the row that is measured next. */
        private var next = 0

        /** The place of the next row, relative to the table. */
        private var top = 0f

        /** True when the table has rows that are not placed yet. */
        val hasMore: Boolean get() = next < table.rows.size || measured.isNotEmpty()

        /**
         * The rows that fit into the given height.
         *
         * At least one row is returned when the table has one, a table is
         * never lost.
         */
        fun take(available: Float): List<Row> {
            val result = ArrayList<Row>()
            var used = 0f
            while (true) {
                val row = measured.firstOrNull() ?: measureNext() ?: break
                if (result.isNotEmpty() && used + row.height > available) break
                measured.removeFirst()
                result += row
                used += row.height
            }
            if (result.isEmpty()) return emptyList()
            // The rows start at the top of the table again, the part of a
            // table that moved to the next page is drawn at the right place.
            val offset = result.first().top
            return result.map { Row(it.top - offset, it.height, it.cells) }
        }

        /**
         * The first row of the table.
         *
         * It is used when the row is taller than the page: it is placed
         * anyway, so that the table is not lost.
         */
        fun firstRow(): Row {
            val row = measured.firstOrNull() ?: measureNext()
            return row ?: Row(0f, 0f, emptyList())
        }

        /** Measures the next row of the table. */
        private fun measureNext(): Row? {
            if (next >= table.rows.size) return null
            val row = measureRow(table.rows[next], top)
            next++
            top += row.height
            measured.addLast(row)
            return row
        }

        /** Measures one row of the table. */
        private fun measureRow(rowCells: List<DocxCell>, rowTop: Float): Row {
            var cells = measureCells(rowCells, 1f)
            var height = cells.maxOfOrNull { cell -> cell.height } ?: 0f
            // A row that is taller than the page would be cut off, its text
            // would be lost. The text of the row is made smaller until it
            // fits onto the page.
            var factor = 1f
            while (height > textHeight && factor > MIN_ROW_FACTOR) {
                factor *= ROW_SHRINK_FACTOR
                cells = measureCells(rowCells, factor)
                height = cells.maxOfOrNull { cell -> cell.height } ?: 0f
            }
            return Row(rowTop, height, cells.map { it.toCell() })
        }

        /** Measures the cells of one row of the table. */
        private fun measureCells(rowCells: List<DocxCell>, factor: Float): List<MeasuredCell> {
            val cells = ArrayList<MeasuredCell>()
            var column = 0
            for (cell in rowCells) {
                val span = cell.columnSpan.coerceAtLeast(1)
                var width = 0f
                for (index in column until min(column + span, widths.size)) {
                    width += widths[index]
                }
                if (width <= 0f) width = textWidth / widths.size
                val padding = dp(4f)
                val inner = (width - padding * 2).toInt().coerceAtLeast(dp(24f).toInt())
                val layouts = cell.blocks
                    .filterIsInstance<DocxBlock.Paragraph>()
                    .map { layoutOf(it, inner, baseSizeFor(it) * factor) }
                val cellHeight = layouts.sumOf { it.height.toDouble() }.toFloat() + padding * 2
                cells += MeasuredCell(
                    left = columnOffset(column, widths),
                    width = width,
                    background = cell.background?.let { OfficeColors.toAndroid(it) },
                    layouts = layouts,
                    vertical = cell.verticalAlignment,
                    height = cellHeight,
                )
                column += span
            }
            return cells
        }
    }

    /** The widths of the columns of a table, in pixels. */
    private fun widthsOf(table: DocxBlock.Table): List<Float> {
        val columnCount = table.rows.maxOfOrNull { row -> row.sumOf { it.columnSpan } } ?: 0
        var widths = table.columnWidthsPt.map { it * scale }.filter { it > 0f }
        if (widths.isEmpty()) {
            val count = columnCount.coerceAtLeast(1)
            widths = List(count) { textWidth / count }
        }
        val total = widths.sum().takeIf { it > 0f } ?: textWidth
        return widths.map { it * textWidth / total }
    }

    private fun columnOffset(column: Int, widths: List<Float>): Float {
        var offset = 0f
        for (index in 0 until min(column, widths.size)) {
            offset += widths[index]
        }
        return offset
    }

    // ------------------------------------------------------------------- text

    /**
     * The last line of the layout that ends above the given height.
     *
     * @param from the first line of the range
     * @return the number of the lines of the range
     */
    private fun lineAt(layout: StaticLayout, available: Float, from: Int): Int {
        if (available <= 0f) return from
        var line = from
        while (line < layout.lineCount) {
            val height = layout.getLineBottom(line) - layout.getLineTop(from)
            if (height > available) break
            line++
        }
        return line
    }

    /** The height of the lines of a layout between the first and the last one. */
    private fun heightOf(layout: StaticLayout, firstLine: Int, lastLine: Int): Float {
        if (lastLine <= firstLine) return 0f
        val bottom = layout.getLineBottom((lastLine - 1).coerceAtMost(layout.lineCount - 1))
        return bottom - layout.getLineTop(firstLine).toFloat()
    }

    private fun layoutOf(
        paragraph: DocxBlock.Paragraph,
        widthPx: Int,
        baseSizePx: Float,
    ): StaticLayout {
        val builder = SpannableStringBuilder()
        for (run in paragraph.runs) {
            if (run.imageBytes != null) continue
            val text = run.text
            if (text.isEmpty()) continue
            val start = builder.length
            builder.append(text)
            val end = builder.length
            if (run.bold || run.italic) {
                val style = when {
                    run.bold && run.italic -> Typeface.BOLD_ITALIC
                    run.bold -> Typeface.BOLD
                    else -> Typeface.ITALIC
                }
                builder.setSpan(StyleSpan(style), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (run.underline) {
                builder.setSpan(UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (run.strike) {
                builder.setSpan(StrikethroughSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (run.superscript) {
                builder.setSpan(SuperscriptSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            run.color?.let {
                builder.setSpan(
                    ForegroundColorSpan(OfficeColors.toAndroid(it)),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            run.sizePt?.let {
                builder.setSpan(
                    AbsoluteSizeSpan((it * scale).roundToInt()),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            run.fontFamily?.takeIf { it.isNotBlank() }?.let {
                builder.setSpan(TypefaceSpan(it), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        if ((paragraph.headingLevel > 0 || paragraph.styleId == "Title") && builder.isNotEmpty()) {
            builder.setSpan(StyleSpan(Typeface.BOLD), 0, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return StaticLayout.Builder
            .obtain(builder, 0, builder.length, textPaint(baseSizePx), widthPx)
            .setAlignment(alignmentOf(paragraph.alignment))
            .setLineSpacing(0f, paragraph.lineSpacing.coerceIn(0.9f, 2.5f))
            .setIncludePad(false)
            .build()
    }

    private fun textPaint(sizePx: Float): TextPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = sizePx
        color = textColour
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private fun baseSizeFor(paragraph: DocxBlock.Paragraph): Float {
        val base = document.defaultFontPt * scale
        return when {
            paragraph.styleId == "Title" -> base * 2f
            paragraph.headingLevel == 1 -> base * 1.6f
            paragraph.headingLevel == 2 -> base * 1.35f
            paragraph.headingLevel == 3 -> base * 1.18f
            paragraph.headingLevel > 0 -> base * 1.08f
            else -> base
        }
    }

    private fun alignmentOf(value: String): Layout.Alignment = when (value) {
        "center" -> Layout.Alignment.ALIGN_CENTER
        "right" -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_NORMAL
    }

    private fun dp(value: Float): Float = value * density

    private companion object {
        const val TAG = "DocxLayout"

        /**
         * The step the text of a row that is too tall is shrunk by.
         *
         * A row that does not fit onto the page would be cut off, its text
         * would be lost.
         */
        const val ROW_SHRINK_FACTOR = 0.85f

        /** The smallest factor the text of a row is shrunk by. */
        const val MIN_ROW_FACTOR = 0.4f
    }
}
