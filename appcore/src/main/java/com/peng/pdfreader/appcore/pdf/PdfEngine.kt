package com.peng.pdfreader.appcore.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Offline PDF engine built on the framework renderer (android.graphics.pdf).
 * Everything runs on device; no page is ever uploaded.
 */
object PdfEngine {

    private const val RENDER_WIDTH = 1240 // ~150 DPI for A4

    data class RenderedPage(val bitmap: Bitmap, val pageWidthPt: Int, val pageHeightPt: Int)

    fun pageCount(file: File): Int = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { it.pageCount }
        }
    }.onFailure { android.util.Log.w("PdfEngine", "pageCount failed for ${file.absolutePath}", it) }
        .getOrDefault(0)

    fun renderPage(file: File, pageIndex: Int, targetWidth: Int = RENDER_WIDTH): RenderedPage? =
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return null
                    renderer.openPage(pageIndex).use { page ->
                        val scale = targetWidth.toFloat() / page.width
                        val width = max(1, (page.width * scale).roundToInt())
                        val height = max(1, (page.height * scale).roundToInt())
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        RenderedPage(bitmap, page.width, page.height)
                    }
                }
            }
        }.getOrNull()

    /**
     * Renders a raster image (png/jpg) as a single "page" so the reader can open
     * the image documents that live in the sandbox as well.
     */
    fun renderImage(file: File, targetWidth: Int = RENDER_WIDTH): RenderedPage? = runCatching {
        val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val scale = targetWidth.toFloat() / decoded.width
        val width = max(1, (decoded.width * scale).roundToInt())
        val height = max(1, (decoded.height * scale).roundToInt())
        val scaled = Bitmap.createScaledBitmap(decoded, width, height, true)
        if (scaled != decoded) decoded.recycle()
        RenderedPage(scaled, 595, max(1, (595 * height.toFloat() / width).toInt()))
    }.getOrNull()

    fun renderThumbnail(file: File, pageIndex: Int, targetWidth: Int = 240): Bitmap? =
        renderPage(file, pageIndex, targetWidth)?.bitmap

    // ------------------------------------------------------------------ signature

    /** Bakes a signature/stamp bitmap into the page and writes a new document. */
    fun flattenOverlay(
        source: File,
        out: File,
        pageIndex: Int,
        overlay: Bitmap,
        bounds: RectF,
    ): Result<Long> = runCatching {
        val document = PdfDocument()
        val total = pageCount(source)
        for (pageNo in 1..total) {
            val rendered = renderPage(source, pageNo - 1) ?: continue
            val info = PdfDocument.PageInfo
                .Builder(rendered.pageWidthPt, rendered.pageHeightPt, pageNo)
                .create()
            val page = document.startPage(info)
            val canvas = page.canvas
            canvas.drawBitmap(
                rendered.bitmap,
                null,
                RectF(0f, 0f, rendered.pageWidthPt.toFloat(), rendered.pageHeightPt.toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            if (pageNo == pageIndex + 1) {
                val scale = rendered.pageWidthPt / 595f
                val dest = RectF(
                    bounds.left * scale,
                    bounds.top * scale,
                    bounds.right * scale,
                    bounds.bottom * scale,
                )
                canvas.drawBitmap(overlay, null, dest, Paint(Paint.FILTER_BITMAP_FLAG))
            }
            document.finishPage(page)
            rendered.bitmap.recycle()
        }
        FileOutputStream(out).use { document.writeTo(it) }
        document.close()
        out.length()
    }

    // ------------------------------------------------------------------ text / outline

    fun extractOutline(file: File): List<OutlineNode> =
        PdfMeta.outline(file)

    fun extractTextByPage(file: File): List<String> = PdfMeta.textByPage(file)

    fun searchText(file: File, keyword: String): List<Pair<Int, String>> {
        if (keyword.isBlank()) return emptyList()
        return extractTextByPage(file).mapIndexedNotNull { index, text ->
            if (text.contains(keyword, ignoreCase = true)) index + 1 to text else null
        }
    }
}
