package com.peng.pdfreader.ui.adapter.listAdapter

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
import com.peng.pdfreader.core.pdf.PdfEngine
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Prints a PDF of the sandbox.
 *
 * The pages are rasterised with the offline renderer of the core package and written
 * back as a PDF, so the print job never needs a network connection and never
 * touches the original file.
 */
class DocumentPrintAdapter(
    private val file: File,
    private val jobName: String,
) : PrintDocumentAdapter() {

    private var pageCount = 0
    private var written = false

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        pageCount = PdfEngine.pageCount(file)
        if (pageCount <= 0) {
            callback.onLayoutFailed("The document has no printable page.")
            return
        }
        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pageCount)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        if (written) {
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            return
        }
        if (cancellationSignal?.isCanceled == true) {
            callback.onWriteCancelled()
            return
        }
        val document = PdfDocument()
        try {
            for (index in 0 until pageCount) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }
                val rendered = PdfEngine.renderPage(file, index) ?: continue
                val info = PdfDocument.PageInfo.Builder(
                    rendered.pageWidthPt,
                    rendered.pageHeightPt,
                    index + 1,
                ).create()
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
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                )
                document.finishPage(page)
                rendered.bitmap.recycle()
            }
            val buffer = ByteArrayOutputStream()
            document.writeTo(buffer)
            ParcelFileDescriptor.AutoCloseOutputStream(destination).use {
                it.write(buffer.toByteArray())
            }
            written = true
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (error: Exception) {
            callback.onWriteFailed(error.message)
        } finally {
            document.close()
        }
    }
}
