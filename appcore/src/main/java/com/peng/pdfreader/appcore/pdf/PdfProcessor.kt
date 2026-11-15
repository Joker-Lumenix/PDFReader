package com.peng.pdfreader.appcore.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.peng.pdfreader.appcore.util.Logger
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The PDF processing engine.
 *
 * Every entry point starts a job on a worker thread and reports the progress
 * through a callback, so the UI never blocks:
 *
 * ```kotlin
 * PdfProcessor.merge(sources, out, onProgress = { progress -> }) { result -> }
 * ```
 *
 * The work itself is ported from the first version of the app. It renders the
 * pages through the framework renderer and writes them into a fresh document,
 * which is the only way to rebuild a PDF with the platform APIs.
 */
object PdfProcessor {

    private const val TAG = "PdfProcessor"
    private const val A4_WIDTH_PT = 595
    private const val A4_HEIGHT_PT = 842
    private const val RENDER_WIDTH = 1240

    /** Two workers keep the queue short without flooding the device. */
    private val executor: ExecutorService = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "pdf-engine").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    // ------------------------------------------------------------------ merge

    /**
     * Concatenates the given documents into [out].
     *
     * @param insertBlankOnOdd adds a blank page after a document that ends on
     * an odd page, so the result can be printed double sided.
     */
    fun merge(
        sources: List<File>,
        out: File,
        insertBlankOnOdd: Boolean = false,
        onProgress: (PdfProgress) -> Unit = {},
        onResult: (Result<Long>) -> Unit,
    ): PdfJob = submit(
        onProgress = onProgress,
        onResult = onResult,
    ) { report ->
        mergeBlocking(sources, out, insertBlankOnOdd, report)
    }

    private fun mergeBlocking(
        sources: List<File>,
        out: File,
        insertBlankOnOdd: Boolean,
        report: (PdfProgress) -> Unit,
    ): Long {
        if (sources.isEmpty()) throw PdfError.Unsupported(PdfError.CODE_NO_FILES)
        val document = PdfDocument()
        var written = 0
        try {
            sources.forEachIndexed { index, source ->
                if (!source.isFile) throw PdfError.UnreadableSource(source.absolutePath)
                val pages = PdfEngine.pageCount(source)
                if (pages <= 0) throw PdfError.UnreadableSource(source.absolutePath)
                for (pageNo in 1..pages) {
                    val rendered = PdfEngine.renderPage(source, pageNo - 1) ?: continue
                    val info = PdfDocument.PageInfo
                        .Builder(rendered.pageWidthPt, rendered.pageHeightPt, written + 1)
                        .create()
                    val page = document.startPage(info)
                    page.canvas.drawBitmap(
                        rendered.bitmap,
                        null,
                        RectF(0f, 0f, rendered.pageWidthPt.toFloat(), rendered.pageHeightPt.toFloat()),
                        Paint(Paint.FILTER_BITMAP_FLAG),
                    )
                    document.finishPage(page)
                    rendered.bitmap.recycle()
                    written++
                }
                if (insertBlankOnOdd && written % 2 == 1) {
                    val info = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, written + 1).create()
                    val page = document.startPage(info)
                    page.canvas.drawColor(Color.WHITE)
                    document.finishPage(page)
                    written++
                }
                report(PdfProgress(PdfStage.MERGE, index + 1, sources.size))
            }
            if (written == 0) throw PdfError.Unsupported(PdfError.CODE_NO_PAGES)
            out.parentFile?.let { if (!it.exists()) it.mkdirs() }
            FileOutputStream(out).use { document.writeTo(it) }
        } finally {
            document.close()
        }
        return out.length()
    }

    // ------------------------------------------------------------------ split

    /**
     * Writes every range into its own document inside [outDir].
     *
     * The ranges are 1 based and inclusive.
     */
    fun split(
        source: File,
        outDir: File,
        ranges: List<IntRange>,
        onProgress: (PdfProgress) -> Unit = {},
        onResult: (Result<List<File>>) -> Unit,
    ): PdfJob = submit(
        onProgress = onProgress,
        onResult = onResult,
    ) { report ->
        splitBlocking(source, outDir, ranges, report)
    }

    /** Writes every page into its own document inside [outDir]. */
    fun splitEveryPage(
        source: File,
        outDir: File,
        onProgress: (PdfProgress) -> Unit = {},
        onResult: (Result<List<File>>) -> Unit,
    ): PdfJob = submit(
        onProgress = onProgress,
        onResult = onResult,
    ) { report ->
        splitEveryPageBlocking(source, outDir, report)
    }

    private fun splitEveryPageBlocking(
        source: File,
        outDir: File,
        report: (PdfProgress) -> Unit,
    ): List<File> {
        val total = PdfEngine.pageCount(source)
        if (total <= 0) throw PdfError.UnreadableSource(source.absolutePath)
        return splitBlocking(source, outDir, (1..total).map { it..it }, report)
    }

    private fun splitBlocking(
        source: File,
        outDir: File,
        ranges: List<IntRange>,
        report: (PdfProgress) -> Unit,
    ): List<File> {
        if (!source.isFile) throw PdfError.UnreadableSource(source.absolutePath)
        val total = PdfEngine.pageCount(source)
        if (total <= 0) throw PdfError.UnreadableSource(source.absolutePath)
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw PdfError.UnwritableTarget(outDir.absolutePath)
        }
        val outputs = ArrayList<File>()
        ranges.forEachIndexed { index, range ->
            val start = maxOf(1, range.first)
            val end = min(total, range.last)
            if (end < start) return@forEachIndexed
            val target = File(outDir, "%s_p%03d-%03d.pdf".format(baseName(source), start, end))
            writePages(source, start..end, target)
            outputs += target
            report(PdfProgress(PdfStage.SPLIT, index + 1, ranges.size))
        }
        if (outputs.isEmpty()) throw PdfError.Unsupported(PdfError.CODE_NO_PAGES)
        return outputs
    }

    private fun writePages(source: File, pages: IntRange, target: File): Long {
        val document = PdfDocument()
        try {
            var index = 0
            for (pageNo in pages) {
                val rendered = PdfEngine.renderPage(source, pageNo - 1) ?: continue
                val info = PdfDocument.PageInfo
                    .Builder(rendered.pageWidthPt, rendered.pageHeightPt, ++index)
                    .create()
                val page = document.startPage(info)
                page.canvas.drawBitmap(
                    rendered.bitmap,
                    null,
                    RectF(0f, 0f, rendered.pageWidthPt.toFloat(), rendered.pageHeightPt.toFloat()),
                    Paint(Paint.FILTER_BITMAP_FLAG),
                )
                document.finishPage(page)
                rendered.bitmap.recycle()
            }
            FileOutputStream(target).use { document.writeTo(it) }
        } finally {
            document.close()
        }
        return target.length()
    }

    // ------------------------------------------------------------------ compress

    /**
     * Recompresses the streams of [source] into [out].
     *
     * @param quality JPEG quality of the re-encoded images, 1 .. 100.
     */
    fun compress(
        source: File,
        out: File,
        quality: Int,
        onProgress: (PdfProgress) -> Unit = {},
        onResult: (Result<CompressResult>) -> Unit,
    ): PdfJob = submit(
        onProgress = onProgress,
        onResult = onResult,
    ) { report ->
        PdfCompressor.compress(source, out, quality, AndroidImageCodec, report).getOrThrow()
    }

    // ------------------------------------------------------------------ images

    /**
     * Places the images into a new document.
     *
     * @param layout grid used when several images share one page
     * @param marginMm page margin in millimetres
     * @param landscape writes landscape pages
     */
    fun imagesToPdf(
        images: List<File>,
        out: File,
        layout: ImageLayout = ImageLayout.Single,
        marginMm: Int = 10,
        landscape: Boolean = false,
        onProgress: (PdfProgress) -> Unit = {},
        onResult: (Result<Long>) -> Unit,
    ): PdfJob = submit(
        onProgress = onProgress,
        onResult = onResult,
    ) { report ->
        imagesToPdfBlocking(images, out, layout, marginMm, landscape, report)
    }

    private fun imagesToPdfBlocking(
        images: List<File>,
        out: File,
        layout: ImageLayout,
        marginMm: Int,
        landscape: Boolean,
        report: (PdfProgress) -> Unit,
    ): Long {
        if (images.isEmpty()) throw PdfError.Unsupported(PdfError.CODE_NO_IMAGES)
        val document = PdfDocument()
        try {
            val margin = marginMm / 25.4f * 72f
            val pageWidth = if (landscape) A4_HEIGHT_PT else A4_WIDTH_PT
            val pageHeight = if (landscape) A4_WIDTH_PT else A4_HEIGHT_PT
            val contentWidth = pageWidth - margin * 2
            val contentHeight = pageHeight - margin * 2
            val cellWidth = contentWidth / layout.columns
            val cellHeight = contentHeight / layout.rows

            images.chunked(layout.perPage).forEachIndexed { pageIndex, chunk ->
                val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIndex + 1).create()
                val page = document.startPage(info)
                page.canvas.drawColor(Color.WHITE)
                chunk.forEachIndexed { slot, item ->
                    val bitmap = decodeImage(item) ?: return@forEachIndexed
                    val column = slot % layout.columns
                    val row = slot / layout.columns
                    val left = margin + column * cellWidth
                    val top = margin + row * cellHeight
                    val scale = min(cellWidth / bitmap.width, cellHeight / bitmap.height)
                    val drawWidth = bitmap.width * scale
                    val drawHeight = bitmap.height * scale
                    val dest = RectF(
                        left + (cellWidth - drawWidth) / 2,
                        top + (cellHeight - drawHeight) / 2,
                        left + (cellWidth + drawWidth) / 2,
                        top + (cellHeight + drawHeight) / 2,
                    )
                    page.canvas.drawBitmap(bitmap, null, dest, Paint(Paint.FILTER_BITMAP_FLAG))
                    bitmap.recycle()
                }
                document.finishPage(page)
                report(PdfProgress(PdfStage.IMAGES, pageIndex + 1, images.size))
            }
            out.parentFile?.let { if (!it.exists()) it.mkdirs() }
            FileOutputStream(out).use { document.writeTo(it) }
        } finally {
            document.close()
        }
        return out.length()
    }

    private fun decodeImage(item: File): Bitmap? = BitmapFactory.decodeFile(item.absolutePath)

    // ------------------------------------------------------------------ ranges

    /**
     * Parses "1-3,5,8-" into ranges.
     *
     * An open end ("8-") stays open, the caller decides where it ends.
     */
    fun parseRangeExpression(expression: String): List<IntRange> {
        val ranges = LinkedHashSet<IntRange>()
        expression.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { part ->
            val bounds = part.split('-', limit = 2).map { it.trim() }
            if (bounds.size == 2) {
                val start = bounds[0].toIntOrNull()
                val end = bounds[1].toIntOrNull()
                when {
                    start == null -> Unit
                    end == null -> ranges.add(IntRange(start, Int.MAX_VALUE))
                    start <= end -> ranges.add(IntRange(start, end))
                    else -> ranges.add(IntRange(end, start))
                }
            } else {
                bounds[0].toIntOrNull()?.let { ranges.add(IntRange(it, it)) }
            }
        }
        return ranges.toList()
    }

    /**
     * Parses "1-3,5,8-" into the page numbers it selects.
     *
     * @param total number of pages of the document, an open end stops here.
     */
    fun parsePageNumbers(expression: String, total: Int): List<Int> {
        val pages = sortedSetOf<Int>()
        parseRangeExpression(expression).forEach { range ->
            val start = maxOf(1, range.first)
            val end = min(total, range.last)
            if (end >= start) for (page in start..end) pages.add(page)
        }
        return pages.toList()
    }

    // ------------------------------------------------------------------ plumbing

    private fun baseName(file: File): String = file.nameWithoutExtension

    /**
     * Runs [work] on a worker thread and hands the result to [onResult].
     *
     * Both callbacks are invoked on the worker thread.
     */
    private fun <T> submit(
        onProgress: (PdfProgress) -> Unit,
        onResult: (Result<T>) -> Unit,
        work: (report: (PdfProgress) -> Unit) -> T,
    ): PdfJob {
        val cancelled = AtomicBoolean(false)
        val holder = AtomicReference<Future<*>?>(null)
        holder.set(executor.submit {
            val outcome = runCatching {
                work { progress ->
                    if (cancelled.get()) throw PdfError.Cancelled()
                    onProgress(progress)
                }
            }
            outcome
                .onFailure { Logger.w(TAG, "PDF job failed: ${it.message}") }
                .let(onResult)
        })
        return object : PdfJob {
            override fun cancel() {
                cancelled.set(true)
                holder.get()?.cancel(true)
            }

            override val isCancelled: Boolean get() = cancelled.get()
        }
    }
}
