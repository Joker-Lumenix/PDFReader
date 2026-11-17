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
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.peng.pdfreader.R
import com.peng.pdfreader.core.office.OfficeColors
import com.peng.pdfreader.core.office.pptx.PptxDeck
import com.peng.pdfreader.core.office.pptx.PptxParagraph
import com.peng.pdfreader.core.office.pptx.PptxShape
import com.peng.pdfreader.core.office.pptx.PptxSlide
import kotlin.math.max
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

    /** The deck that is rendered. */
    var deck: PptxDeck? = null
        set(value) {
            field = value
            adapter.deck = value
            adapter.notifyDataSetChanged()
            if (value != null && value.slides.isNotEmpty()) {
                pager.setCurrentItem(0, false)
            }
        }

    /** Raised with the index of the slide the pager shows. */
    var onSlideChanged: ((Int) -> Unit)? = null

    /** Index of the slide the pager shows. */
    val currentSlide: Int get() = pager.currentItem

    init {
        addView(
            pager,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        pager.adapter = adapter
        pager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    onSlideChanged?.invoke(position)
                }
            },
        )
    }

    /** Shows the slide with the given index. */
    fun setSlide(index: Int) {
        if (index in 0 until adapter.itemCount) pager.currentItem = index
    }

    private inner class SlideAdapter : RecyclerView.Adapter<SlideHolder>() {

        var deck: PptxDeck? = null

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
            val current = deck ?: return
            holder.view.bind(current.slides.getOrNull(position), current)
        }

        override fun getItemCount(): Int = deck?.slides?.size ?: 0
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
    private var deck: PptxDeck? = null

    /** The bitmaps of the pictures of the slide, every picture is decoded once. */
    private val bitmaps = HashMap<PptxShape.Picture, Bitmap>()

    /** Binds a slide to the view. */
    fun bind(slide: PptxSlide?, deck: PptxDeck) {
        this.slide = slide
        this.deck = deck
        bitmaps.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val current = slide ?: return
        val currentDeck = deck ?: return
        if (width == 0 || height == 0) return

        val scale = min(
            width / currentDeck.widthDp.coerceAtLeast(1f),
            height / currentDeck.heightDp.coerceAtLeast(1f),
        )
        val left = (width - currentDeck.widthDp * scale) / 2f
        val top = (height - currentDeck.heightDp * scale) / 2f
        val slideRect = RectF(
            left,
            top,
            left + currentDeck.widthDp * scale,
            top + currentDeck.heightDp * scale,
        )

        current.background?.let {
            fillPaint.color = OfficeColors.toAndroid(it)
            canvas.drawRect(slideRect, fillPaint)
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
            }
        }
        canvas.restore()
    }

    private fun drawRectangle(canvas: Canvas, shape: PptxShape.Rectangle, scale: Float) {
        val rect = shape.rect(scale)
        shape.fill?.let {
            fillPaint.color = OfficeColors.toAndroid(it)
            canvas.drawRect(rect, fillPaint)
        }
        shape.line?.let {
            linePaint.color = OfficeColors.toAndroid(it)
            linePaint.strokeWidth = max(1f, shape.lineWidthDp * scale)
            canvas.drawRect(rect, linePaint)
        }
    }

    private fun drawPicture(canvas: Canvas, shape: PptxShape.Picture, scale: Float) {
        val bitmap = bitmaps.getOrPut(shape) { decode(shape.bytes) ?: return }
        val rect = shape.rect(scale)
        canvas.drawBitmap(bitmap, null, rect, bitmapPaint)
    }

    private fun drawTextBox(canvas: Canvas, shape: PptxShape.TextBox, scale: Float) {
        val rect = shape.rect(scale)
        shape.fill?.let {
            fillPaint.color = OfficeColors.toAndroid(it)
            canvas.drawRect(rect, fillPaint)
        }
        val layouts = shape.paragraphs.map { buildLayout(it, rect.width(), scale) }
        val total = layouts.sumOf { it.height.toDouble() }.toFloat()
        var y = rect.top
        if (shape.anchor == "center") {
            y = rect.top + (rect.height() - total) / 2f
        } else if (shape.anchor == "bottom") {
            y = rect.bottom - total
        }
        layouts.forEachIndexed { index, layout ->
            val paragraph = shape.paragraphs.getOrNull(index)
            val bullet = paragraph?.bullet
            // A bulleted paragraph keeps a gutter for its bullet.
            val gutter = if (bullet != null) indentOf(paragraph, scale) else 0f
            canvas.save()
            canvas.translate(rect.left + gutter, y)
            layout.draw(canvas)
            canvas.restore()
            if (bullet != null) {
                bulletPaint.color = paragraph.style.color?.let { OfficeColors.toAndroid(it) }
                    ?: context.getColor(R.color.text_primary)
                bulletPaint.textSize = -layout.getLineAscent(0) * 0.8f
                canvas.drawText(
                    bullet,
                    rect.left + paragraph.level * gutter * 0.5f,
                    y + layout.getLineBaseline(0),
                    bulletPaint,
                )
            }
            y += layout.height
        }
    }

    private fun drawTable(canvas: Canvas, shape: PptxShape.Table, scale: Float) {
        val rect = shape.rect(scale)
        val borderColour = context.getColor(R.color.surface_border_strong)
        var y = rect.top
        shape.rows.forEachIndexed { rowIndex, row ->
            val rowHeight = shape.rowHeightsDp.getOrNull(rowIndex)?.times(scale) ?: (24f * scale)
            var x = rect.left
            row.forEachIndexed { columnIndex, cell ->
                val columnWidth = shape.columnWidthsDp.getOrNull(columnIndex)?.times(scale) ?: (80f * scale)
                cell.background?.let {
                    fillPaint.color = OfficeColors.toAndroid(it)
                    canvas.drawRect(x, y, x + columnWidth, y + rowHeight, fillPaint)
                }
                val colour = cell.color?.let { OfficeColors.toAndroid(it) } ?: context.getColor(R.color.text_primary)
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = colour
                    // The text of a cell follows the height of its row.
                    textSize = max(6f * scale, rowHeight * 0.45f)
                    typeface = Typeface.create(Typeface.DEFAULT, if (cell.bold) Typeface.BOLD else Typeface.NORMAL)
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
            linePaint.strokeWidth = density
            canvas.drawLine(rect.left, y + rowHeight, rect.right, y + rowHeight, linePaint)
            y += rowHeight
        }
    }

    /**
     * Builds the layout of one paragraph.
     *
     * Every paragraph gets its own paint: a layout keeps the paint it was
     * built with, so a shared one would draw every paragraph with the size of
     * the last one.
     */
    private fun buildLayout(paragraph: PptxParagraph, widthPx: Float, scale: Float): StaticLayout {
        val style = paragraph.style
        val builder = SpannableStringBuilder()
        builder.append(paragraph.text)
        if (style.bold || style.italic) {
            val typeface = when {
                style.bold && style.italic -> Typeface.BOLD_ITALIC
                style.bold -> Typeface.BOLD
                else -> Typeface.ITALIC
            }
            builder.setSpan(StyleSpan(typeface), 0, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (style.underline) {
            builder.setSpan(UnderlineSpan(), 0, builder.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.color?.let { OfficeColors.toAndroid(it) }
                ?: context.getColor(R.color.text_primary)
            // One point is 4/3 of a deck dp.
            textSize = max(MIN_TEXT_SIZE_DP * scale, style.sizePt * scale * 4f / 3f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val indent = indentOf(paragraph, scale)
        val alignment = when (style.alignment) {
            "center" -> Layout.Alignment.ALIGN_CENTER
            "right" -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        return StaticLayout.Builder
            .obtain(builder, 0, builder.length, paint, max(1, (widthPx - indent).toInt()))
            .setAlignment(alignment)
            .setIncludePad(false)
            .build()
    }

    /** The gutter a bulleted paragraph keeps for its bullet. */
    private fun indentOf(paragraph: PptxParagraph?, scale: Float): Float =
        if (paragraph?.bullet != null) BULLET_INDENT_DP * scale else 0f

    private fun decode(bytes: ByteArray): Bitmap? =
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

    private fun dp(value: Float): Float = value * density

    private companion object {
        /** The smallest text of a slide, in deck dp. */
        const val MIN_TEXT_SIZE_DP = 8f

        /** The gutter of a bulleted paragraph, in deck dp. */
        const val BULLET_INDENT_DP = 16f
    }
}

/** The pixel rectangle of a shape. */
private fun PptxShape.rect(scale: Float): RectF = RectF(
    leftDp * scale,
    topDp * scale,
    (leftDp + widthDp) * scale,
    (topDp + heightDp) * scale,
)
