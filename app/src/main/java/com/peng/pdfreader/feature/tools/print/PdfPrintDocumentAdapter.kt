package com.peng.pdfreader.feature.tools.print

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import com.peng.pdfreader.appcore.pdf.PdfEngine
import com.peng.pdfreader.appcore.util.Logger
import java.io.File
import java.io.FileOutputStream

/**
 * Prints a PDF with the framework print service.
 *
 * The adapter hands the source document to the print service unchanged when
 * the whole document is printed. A selection of pages is rasterised with the
 * framework renderer and written as a new document.
 */
class PdfPrintDocumentAdapter(
    private val source: File,
    private val pageCount: Int,
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(source.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pageCount.coerceAtLeast(1))
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onWriteCancelled()
            return
        }
        val requested = requestedPages(pages)
        val written = runCatching {
            if (requested.size == pageCount && requested.firstOrNull() == 1) {
                copySource(destination)
            } else {
                writeSelection(requested, destination)
            }
        }.onFailure { Logger.e(TAG, "printing failed", it) }

        if (written.isFailure) {
            callback.onWriteFailed(written.exceptionOrNull()?.message)
            return
        }
        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
    }

    private fun requestedPages(pages: Array<out PageRange>): IntArray {
        if (pages.isEmpty()) return IntArray(0)
        val result = sortedSetOf<Int>()
        pages.forEach { range ->
            for (page in range.start..range.end) {
                if (page in 1..pageCount) result.add(page)
            }
        }
        return result.toIntArray()
    }

    private fun copySource(destination: ParcelFileDescriptor) {
        FileOutputStream(destination.fileDescriptor).use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        }
    }

    private fun writeSelection(pages: IntArray, destination: ParcelFileDescriptor) {
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, pageNumber ->
                val rendered = PdfEngine.renderPage(source, pageNumber - 1) ?: return@forEachIndexed
                val info = PdfDocument.PageInfo
                    .Builder(rendered.pageWidthPt, rendered.pageHeightPt, index + 1)
                    .create()
                val page = document.startPage(info)
                page.canvas.drawBitmap(
                    rendered.bitmap,
                    null,
                    RectF(
                        0f,
                        0f,
                        rendered.pageWidthPt.toFloat(),
                        rendered.pageHeightPt.toFloat(),
                    ),
                    Paint(Paint.FILTER_BITMAP_FLAG),
                )
                document.finishPage(page)
                rendered.bitmap.recycle()
            }
            FileOutputStream(destination.fileDescriptor).use { output ->
                document.writeTo(output)
            }
        } finally {
            document.close()
        }
    }

    companion object {
        private const val TAG = "PdfPrint"
    }
}
