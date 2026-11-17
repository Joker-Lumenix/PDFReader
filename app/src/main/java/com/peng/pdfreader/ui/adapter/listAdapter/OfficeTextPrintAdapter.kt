package com.peng.pdfreader.ui.adapter.listAdapter

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import java.io.ByteArrayOutputStream

/**
 * Prints a Word / Excel / PowerPoint document.
 *
 * The print service of the system rasterises PDFs, so the text of the document
 * is drawn on the pages by the adapter itself: the pages are A4, the text is
 * wrapped at the width of the page.
 */
class OfficeTextPrintAdapter(
    private val jobName: String,
    private val lines: List<String>,
) : PrintDocumentAdapter() {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TEXT_SIZE_PT
        color = Color.BLACK
    }

    /** The lines of the document, wrapped and split into the pages. */
    private fun layout(): List<List<String>> {
        val maxWidth = PAGE_WIDTH_PT - MARGIN_PT * 2
        val pages = mutableListOf<List<String>>()
        var page = mutableListOf<String>()
        var y = MARGIN_PT
        lines.forEach { line ->
            wrap(line, maxWidth).forEach { part ->
                if (y + LEADING_PT > PAGE_HEIGHT_PT - MARGIN_PT) {
                    pages += page
                    page = mutableListOf()
                    y = MARGIN_PT
                }
                page += part
                y += LEADING_PT
            }
        }
        if (page.isNotEmpty()) pages += page
        return pages
    }

    /** Splits a line into the parts that fit on the width of the page. */
    private fun wrap(line: String, maxWidth: Float): List<String> {
        if (line.isEmpty()) return listOf("")
        if (textPaint.measureText(line) <= maxWidth) return listOf(line)
        val parts = mutableListOf<String>()
        var current = StringBuilder()
        line.forEach { char ->
            if (textPaint.measureText(current.toString() + char) > maxWidth && current.isNotEmpty()) {
                parts += current.toString()
                current = StringBuilder()
            }
            current.append(char)
        }
        if (current.isNotEmpty()) parts += current.toString()
        return parts
    }

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
        val pages = layout()
        if (pages.isEmpty()) {
            callback.onLayoutFailed("The document has no printable page.")
            return
        }
        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pages.size)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onWriteCancelled()
            return
        }
        val document = PdfDocument()
        try {
            layout().forEachIndexed { index, page ->
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }
                val info = PdfDocument.PageInfo.Builder(
                    PAGE_WIDTH_PT.toInt(),
                    PAGE_HEIGHT_PT.toInt(),
                    index + 1,
                ).create()
                val pdfPage = document.startPage(info)
                var y = MARGIN_PT + TEXT_SIZE_PT
                page.forEach { line ->
                    pdfPage.canvas.drawText(line, MARGIN_PT, y, textPaint)
                    y += LEADING_PT
                }
                document.finishPage(pdfPage)
            }
            val buffer = ByteArrayOutputStream()
            document.writeTo(buffer)
            ParcelFileDescriptor.AutoCloseOutputStream(destination).use {
                it.write(buffer.toByteArray())
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (error: Exception) {
            callback.onWriteFailed(error.message)
        } finally {
            document.close()
        }
    }

    private companion object {
        /** A4 in points. */
        const val PAGE_WIDTH_PT = 595f
        const val PAGE_HEIGHT_PT = 842f

        /** The margin of the page in points. */
        const val MARGIN_PT = 48f

        /** The size of the text in points. */
        const val TEXT_SIZE_PT = 11f

        /** The distance between two lines in points. */
        const val LEADING_PT = 15f
    }
}
