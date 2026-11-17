package com.peng.pdfreader.core.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
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
import android.util.AttributeSet
import android.view.View
import com.peng.pdfreader.R
import com.peng.pdfreader.core.office.OfficeColors
import com.peng.pdfreader.core.office.docx.DocxBlock
import com.peng.pdfreader.core.office.docx.DocxDocument
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders a parsed Word document.
 *
 * The view lays the document out itself: paragraphs (with their run styles,
 * headings and bullet lists), tables (with cell background and borders) and
 * images. It measures the height of its content, so it has to be hosted by a
 * scrolling parent such as a `ScrollView`.
 */
class DocxPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private class DocxLayout(
        val items: List<Item>,
        val contentHeight: Float,
        val pageHeight: Float,
    )

    private sealed interface Item {
        val top: Float

        class Paragraph(
            override val top: Float,
            val left: Float,
            val text: StaticLayout,
            val bullet: String?,
            val bulletLeft: Float,
        ) : Item

        class Table(
            override val top: Float,
            val left: Float,
            val widths: List<Float>,
            val rows: List<Row>,
            val height: Float,
        ) : Item

        class Picture(
            override val top: Float,
            val rect: RectF,
            val bitmap: Bitmap,
        ) : Item
    }

    private class Row(val top: Float, val height: Float, val cells: List<Cell>)

    private class Cell(
        val left: Float,
        val width: Float,
        val background: Int?,
        val layouts: List<StaticLayout>,
        val vertical: String,
    )

    private val density = resources.displayMetrics.density

    private val textColour = context.getColor(R.color.text_primary)
    private val borderColour = context.getColor(R.color.surface_border_strong)
    private val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColour }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = borderColour
        strokeWidth = density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    private var layout: DocxLayout? = null
    private var layoutDirty = true
    private var layoutWidth = 0
    private var reportedPage = -1

    /** The document that is rendered. */
    var document: DocxDocument? = null
        set(value) {
            field = value
            layoutDirty = true
            reportedPage = -1
            requestLayout()
            invalidate()
        }

    /** Raised with the index of the page the viewport currently shows. */
    var onPageChanged: ((Int) -> Unit)? = null

    /** Number of pages the document spans. */
    var pageCount: Int = 1
        private set

    init {
        isClickable = false
    }

    /**
     * Reports the scroll offset of the hosting container.
     *
     * The view is hosted by a `ScrollView`, which scrolls itself, so the page
     * of the viewport can only be derived from the offset of the container.
     */
    fun updateScrollOffset(scrollY: Int) {
        val current = layout ?: return
        if (current.pageHeight <= 0f) return
        val page = (scrollY / current.pageHeight).toInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (page != reportedPage) {
            reportedPage = page
            onPageChanged?.invoke(page)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (layout == null || layoutDirty || layoutWidth != width) {
            buildLayout(width)
            layoutWidth = width
        }
        val contentHeight = layout?.contentHeight ?: 0f
        val height = when (MeasureSpec.getMode(heightMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> ceil(contentHeight).toInt()
            else -> MeasureSpec.getSize(heightMeasureSpec)
        }
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        val current = layout ?: return
        current.items.forEach { item ->
            when (item) {
                is Item.Paragraph -> drawParagraph(canvas, item)
                is Item.Table -> drawTable(canvas, item)
                is Item.Picture -> canvas.drawBitmap(item.bitmap, null, item.rect, bitmapPaint)
            }
        }
    }

    // ------------------------------------------------------------------ drawing

    private fun drawParagraph(canvas: Canvas, item: Item.Paragraph) {
        canvas.save()
        canvas.translate(item.left, item.top)
        item.text.draw(canvas)
        canvas.restore()
        val bullet = item.bullet ?: return
        bulletPaint.textSize = item.text.getLineAscent(0).let { -it } * 0.8f
        val baseline = item.top + item.text.getLineBaseline(0)
        canvas.drawText(bullet, item.bulletLeft, baseline, bulletPaint)
    }

    private fun drawTable(canvas: Canvas, item: Item.Table) {
        item.rows.forEach { row ->
            row.cells.forEach { cell ->
                if (cell.background != null) {
                    fillPaint.color = cell.background
                    canvas.drawRect(
                        cell.left,
                        row.top,
                        cell.left + cell.width,
                        row.top + row.height,
                        fillPaint,
                    )
                }
                val textHeight = cell.layouts.sumOf { it.height.toDouble() }.toFloat()
                var textTop = row.top + dp(4f)
                if (cell.vertical == "center") {
                    textTop = row.top + (row.height - textHeight) / 2f
                } else if (cell.vertical == "bottom") {
                    textTop = row.top + row.height - textHeight - dp(4f)
                }
                cell.layouts.forEach { text ->
                    canvas.save()
                    canvas.translate(cell.left + dp(4f), textTop)
                    text.draw(canvas)
                    canvas.restore()
                    textTop += text.height
                }
                canvas.drawRect(
                    cell.left,
                    row.top,
                    cell.left + cell.width,
                    row.top + row.height,
                    borderPaint,
                )
            }
        }
    }

    // ------------------------------------------------------------------ layout

    private fun buildLayout(widthPx: Int) {
        val doc = document
        if (doc == null || widthPx <= 0) {
            layout = null
            layoutDirty = false
            return
        }
        val scale = docScale
        val marginLeft = (doc.marginLeftPt * scale).coerceIn(dp(8f), dp(40f))
        val marginRight = (doc.marginRightPt * scale).coerceIn(dp(8f), dp(40f))
        val contentWidth = (widthPx - marginLeft - marginRight).coerceAtLeast(dp(160f))
        val items = ArrayList<Item>()
        var y = (doc.marginTopPt * scale).coerceIn(dp(8f), dp(40f))

        for (block in doc.blocks) {
            when (block) {
                is DocxBlock.Paragraph -> {
                    val indent = block.indentLevel * dp(16f)
                    val bullet = block.listLabel?.takeIf { it.isNotBlank() }
                    val bulletWidth = if (bullet == null) 0f else dp(24f)
                    val textWidth = (contentWidth - indent - bulletWidth)
                        .toInt()
                        .coerceAtLeast(dp(80f).toInt())
                    val text = buildTextLayout(block, textWidth, baseSizeFor(block))
                    y += block.spaceBeforePt * scale
                    items += Item.Paragraph(
                        y,
                        marginLeft + indent + bulletWidth,
                        text,
                        bullet,
                        marginLeft + indent,
                    )
                    y += text.height + block.spaceAfterPt * scale

                    for (run in block.runs) {
                        val bytes = run.imageBytes ?: continue
                        val picture = decode(bytes) ?: continue
                        val width = (run.imageWidthPt * scale).takeIf { it > 0f } ?: contentWidth
                        val height = (run.imageHeightPt * scale).takeIf { it > 0f }
                            ?: width * picture.height / picture.width
                        val scaled = fitInside(picture, width, height)
                        val left = marginLeft + (contentWidth - scaled.width) / 2f
                        items += Item.Picture(y, RectF(left, y, left + scaled.width, y + scaled.height), scaled)
                        y += scaled.height + dp(8f)
                    }
                }

                is DocxBlock.Table -> {
                    val table = buildTable(block, contentWidth, marginLeft, y)
                    items += table
                    y += table.height
                }

                is DocxBlock.Image -> {
                    val picture = decode(block.bytes) ?: continue
                    val width = (block.widthPt * scale).takeIf { it > 0f } ?: contentWidth
                    val height = (block.heightPt * scale).takeIf { it > 0f }
                        ?: contentWidth * picture.height / picture.width
                    val scaled = fitInside(picture, width, height)
                    val left = when (block.alignment) {
                        "center" -> marginLeft + (contentWidth - scaled.width) / 2f
                        "right" -> marginLeft + contentWidth - scaled.width
                        else -> marginLeft
                    }
                    items += Item.Picture(y, RectF(left, y, left + scaled.width, y + scaled.height), scaled)
                    y += scaled.height + dp(8f)
                }
            }
        }

        y += (doc.marginBottomPt * scale).coerceIn(dp(8f), dp(40f))
        val pageHeight = (doc.pageHeightPt * scale).coerceAtLeast(dp(100f))
        layout = DocxLayout(items, y, pageHeight)
        pageCount = max(1, ceil(y / pageHeight).toInt())
        layoutDirty = false
    }

    private fun buildTable(
        table: DocxBlock.Table,
        contentWidth: Float,
        marginLeft: Float,
        startY: Float,
    ): Item.Table {
        val columnCount = table.rows.maxOfOrNull { row -> row.sumOf { it.columnSpan } } ?: 0
        var widths = table.columnWidthsPt.map { it * docScale }.filter { it > 0f }
        if (widths.isEmpty()) {
            val count = columnCount.coerceAtLeast(1)
            widths = List(count) { contentWidth / count }
        }
        val total = widths.sum().takeIf { it > 0f } ?: contentWidth
        widths = widths.map { it * contentWidth / total }

        val rows = ArrayList<Row>()
        var y = startY
        for (rowCells in table.rows) {
            val cells = ArrayList<Cell>()
            var column = 0
            var rowHeight = 0f
            for (cell in rowCells) {
                val span = cell.columnSpan.coerceAtLeast(1)
                var width = 0f
                for (index in column until min(column + span, widths.size)) {
                    width += widths[index]
                }
                if (width <= 0f) width = contentWidth / widths.size
                val padding = dp(4f)
                val inner = (width - padding * 2).toInt().coerceAtLeast(dp(24f).toInt())
                val layouts = cell.blocks
                    .filterIsInstance<DocxBlock.Paragraph>()
                    .map { buildTextLayout(it, inner, baseSizeFor(it)) }
                val cellHeight = layouts.sumOf { it.height.toDouble() }.toFloat() + padding * 2
                rowHeight = max(rowHeight, cellHeight)
                cells += Cell(
                    left = marginLeft + columnOffset(column, widths),
                    width = width,
                    background = cell.background?.let { OfficeColors.toAndroid(it) },
                    layouts = layouts,
                    vertical = cell.verticalAlignment,
                )
                column += span
            }
            rows += Row(y, rowHeight, cells)
            y += rowHeight
        }
        return Item.Table(startY, marginLeft, widths, rows, y - startY)
    }

    private fun buildTextLayout(
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
                    AbsoluteSizeSpan((it * docScale).roundToInt()),
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

    private fun textPaint(sizePx: Float): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizePx
        color = textColour
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private fun baseSizeFor(paragraph: DocxBlock.Paragraph): Float {
        val doc = document ?: return dp(14f)
        val base = doc.defaultFontPt * docScale
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

    private fun columnOffset(column: Int, widths: List<Float>): Float {
        var offset = 0f
        for (index in 0 until min(column, widths.size)) {
            offset += widths[index]
        }
        return offset
    }

    private fun decode(bytes: ByteArray): Bitmap? =
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

    private fun fitInside(bitmap: Bitmap, width: Float, height: Float): Bitmap {
        if (width <= 0f || height <= 0f) return bitmap
        val scale = min(width / bitmap.width, height / bitmap.height)
        if (scale >= 1f && bitmap.width <= width && bitmap.height <= height) return bitmap
        val targetWidth = max(1, (bitmap.width * scale).roundToInt())
        val targetHeight = max(1, (bitmap.height * scale).roundToInt())
        return runCatching { Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true) }
            .getOrDefault(bitmap)
    }

    /** Points are rendered at 1.2 dp, which keeps a body text readable. */
    private val docScale: Float get() = density * 1.2f

    private fun dp(value: Float): Float = value * density
}
