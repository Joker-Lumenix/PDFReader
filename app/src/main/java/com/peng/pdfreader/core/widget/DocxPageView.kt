package com.peng.pdfreader.core.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import android.view.View
import com.peng.pdfreader.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws one page of a Word document.
 *
 * The page is the white sheet of the document, the items of the page are
 * drawn on it. The pictures of the page are decoded when the page is drawn
 * and they are kept in a small cache, so a picture is decoded once and not
 * once per page.
 */
internal class DocxPageView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    private val textColour = context.getColor(R.color.text_primary)
    private val borderColour = context.getColor(R.color.surface_border_strong)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = borderColour
        strokeWidth = density
    }
    private val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = textColour }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }

    /**
     * The decoded pictures of the pages.
     *
     * The key is the bytes of the picture, the model keeps one instance of
     * them for every picture of the document.
     */
    private val bitmaps = object : LruCache<ByteArray, Bitmap>(BITMAP_CACHE_KB) {
        override fun sizeOf(key: ByteArray, value: Bitmap): Int = value.byteCount / 1024
    }

    private var page: DocxLayoutEngine.Page? = null

    /** Binds a page to the view. */
    fun bind(page: DocxLayoutEngine.Page?) {
        this.page = page
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val current = page ?: return
        if (width == 0 || height == 0) return

        // The sheet of the page.
        fillPaint.color = Color.WHITE
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)

        current.items.forEach { item ->
            when (item) {
                is DocxLayoutEngine.Item.Paragraph -> drawParagraph(canvas, item)
                is DocxLayoutEngine.Item.Table -> drawTable(canvas, item)
                is DocxLayoutEngine.Item.Picture -> drawPicture(canvas, item)
            }
        }

        borderPaint.color = borderColour
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)
    }

    private fun drawParagraph(canvas: Canvas, item: DocxLayoutEngine.Item.Paragraph) {
        val layout = item.text
        val offset = layout.getLineTop(item.firstLine)
        val bottom = layout.getLineBottom((item.lastLine - 1).coerceAtMost(layout.lineCount - 1))
        val visible = bottom - offset
        canvas.save()
        canvas.clipRect(
            item.left,
            item.top,
            item.left + layout.width,
            item.top + visible,
        )
        canvas.translate(item.left, item.top - offset)
        layout.draw(canvas)
        canvas.restore()

        val bullet = item.bullet ?: return
        bulletPaint.textSize = -layout.getLineAscent(item.firstLine) * 0.8f
        val baseline = item.top + layout.getLineBaseline(item.firstLine)
        canvas.drawText(bullet, item.bulletLeft, baseline, bulletPaint)
    }

    private fun drawTable(canvas: Canvas, item: DocxLayoutEngine.Item.Table) {
        item.rows.forEach { row ->
            row.cells.forEach { cell ->
                val left = item.left + cell.left
                val top = item.top + row.top
                if (cell.background != null) {
                    fillPaint.color = cell.background
                    canvas.drawRect(left, top, left + cell.width, top + row.height, fillPaint)
                }
                val textHeight = cell.layouts.sumOf { it.height.toDouble() }.toFloat()
                var textTop = top + dp(4f)
                if (cell.vertical == "center") {
                    textTop = top + (row.height - textHeight) / 2f
                } else if (cell.vertical == "bottom") {
                    textTop = top + row.height - textHeight - dp(4f)
                }
                cell.layouts.forEach { text ->
                    canvas.save()
                    canvas.translate(left + dp(4f), textTop)
                    text.draw(canvas)
                    canvas.restore()
                    textTop += text.height
                }
                canvas.drawRect(left, top, left + cell.width, top + row.height, borderPaint)
            }
        }
    }

    private fun drawPicture(canvas: Canvas, item: DocxLayoutEngine.Item.Picture) {
        val bitmap = bitmapOf(item.bytes, item.rect.width(), item.rect.height()) ?: return
        canvas.drawBitmap(bitmap, null, item.rect, bitmapPaint)
    }

    /**
     * The bitmap of a picture, decoded for the size it is drawn at.
     *
     * A picture that is bigger than the page is scaled down while it is
     * decoded, so a big picture does not fill the memory of the phone.
     */
    private fun bitmapOf(bytes: ByteArray, targetWidth: Float, targetHeight: Float): Bitmap? {
        bitmaps.get(bytes)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull() ?: return null
        bitmaps.put(bytes, bitmap)
        return bitmap
    }

    /** The factor the picture is scaled down by while it is decoded. */
    private fun sampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Float,
        targetHeight: Float,
    ): Int {
        val width = targetWidth.coerceAtLeast(1f)
        val height = targetHeight.coerceAtLeast(1f)
        var sample = 1
        while (sourceWidth / (sample * 2) >= width &&
            sourceHeight / (sample * 2) >= height
        ) {
            sample *= 2
        }
        return sample
    }

    private fun dp(value: Float): Float = value * density

    private companion object {
        /** The size of the cache of the decoded pictures in kilobyte. */
        const val BITMAP_CACHE_KB = 12 * 1024
    }
}
