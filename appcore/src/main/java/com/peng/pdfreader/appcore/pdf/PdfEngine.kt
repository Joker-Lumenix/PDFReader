package com.peng.pdfreader.appcore.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.peng.pdfreader.appcore.util.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Offline PDF engine built on the framework renderer (android.graphics.pdf).
 * Everything runs on device; no page is ever uploaded.
 */
object PdfEngine {

    private const val TAG = "PdfEngine"

    private const val RENDER_WIDTH = 1240 // ~150 DPI for A4

    /**
     * The largest bitmap a page may be rendered into.
     *
     * Four megapixels are 16 MB as ARGB_8888, that fits into the heap of a
     * phone several times over. A page that would need more is scaled down:
     * the reader draws the page scaled anyway, so the extra pixels would only
     * cost memory.
     */
    private const val MAX_PAGE_PIXELS = 4L * 1024L * 1024L

    /** The widest bitmap a page may be rendered into. */
    private const val MAX_PAGE_WIDTH = 2048

    /** How many pages are rasterised at the same time. */
    private const val RENDER_WORKERS = 2

    /** How many documents stay open at the same time. */
    private const val MAX_OPEN_DOCUMENTS = 4

    /** How much of the head and of the tail is scanned for an /Encrypt entry. */
    private const val ENCRYPT_PROBE = 16L * 1024L

    data class RenderedPage(val bitmap: Bitmap, val pageWidthPt: Int, val pageHeightPt: Int)

    /**
     * The workers that rasterise the pages.
     *
     * The pool is shared by every page view of the app. A document is parsed
     * once and its pages are rasterised one after the other, so a small pool
     * is enough and it keeps the memory pressure low.
     */
    val workers: ExecutorService = Executors.newFixedThreadPool(RENDER_WORKERS) { runnable ->
        Thread(runnable, "pdf-render").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    // ------------------------------------------------------------------ documents

    /**
     * An open document.
     *
     * Parsing a document is the expensive part of the rendering, so the
     * renderer stays open while the reader works on the document instead of
     * being rebuilt for every single page. The framework renderer only
     * allows one open page at a time, therefore every access to the document
     * goes through the lock of the session.
     */
    private class Session(val renderer: PdfRenderer) {
        val lock = ReentrantLock()
    }

    /**
     * The open documents.
     *
     * The map keeps the order of the access, so the document that was used
     * the longest time ago is the first one that is closed.
     */
    private val sessions = LinkedHashMap<String, Session>(8, 0.75f, true)

    /** The identity of a document: its path and the state of its content. */
    private fun keyOf(file: File): String =
        "${file.absolutePath}|${file.lastModified()}|${file.length()}"

    /**
     * The open session of the document, null when it cannot be opened.
     *
     * The session is cached, so the pages of a document are rendered from a
     * single parse of the file.
     */
    private fun sessionOf(file: File): Session? {
        val key = keyOf(file)
        synchronized(sessions) {
            sessions[key]?.let { return it }
            // A document that was changed is opened again, the session of the
            // older state of the file is given back.
            val prefix = "${file.absolutePath}|"
            sessions.keys.filter { it.startsWith(prefix) }.forEach { stale ->
                sessions.remove(stale)?.let { closeSession(it) }
            }
            val session = createSession(file)
            if (session != null) {
                sessions[key] = session
                // Only a few documents stay open, the others are closed.
                while (sessions.size > MAX_OPEN_DOCUMENTS) {
                    val eldest = sessions.entries.firstOrNull() ?: break
                    sessions.remove(eldest.key)
                    closeSession(eldest.value)
                }
            }
            return session
        }
    }

    /**
     * Opens the document of the file.
     */
    private fun createSession(file: File): Session? {
        val descriptor = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (error: Throwable) {
            Logger.e(TAG, "cannot open ${file.absolutePath}", error)
            return null
        }
        return try {
            val renderer = PdfRenderer(descriptor)
            Session(renderer)
        } catch (error: Throwable) {
            runCatching { descriptor.close() }
            Logger.e(TAG, "cannot parse ${file.absolutePath}", error)
            null
        }
    }

    /**
     * Closes a session and gives the memory it holds back.
     */
    private fun closeSession(session: Session) {
        try {
            session.lock.withLock { session.renderer.close() }
        } catch (error: Throwable) {
            Logger.w(TAG, "cannot close a document: ${error.message}")
        }
    }

    /**
     * Closes the document and gives the memory it holds back.
     *
     * The reader calls this when it leaves the document.
     */
    fun release(file: File) {
        val prefix = "${file.absolutePath}|"
        synchronized(sessions) {
            sessions.keys.filter { it.startsWith(prefix) }.forEach { stale ->
                sessions.remove(stale)?.let { closeSession(it) }
            }
        }
    }

    /**
     * Opens the document and reports what went wrong.
     *
     * The result carries a stable code of [PdfError], the app maps it onto a
     * localised message.
     */
    fun open(file: File): PdfOpen {
        if (!file.isFile) return PdfOpen.Failed(PdfError.CODE_UNREADABLE)
        val session = sessionOf(file)
        if (session == null) {
            // The renderer refuses an encrypted document and a broken one with
            // the very same message, so the trailer decides which one it was.
            return if (isEncrypted(file)) {
                PdfOpen.Failed(PdfError.CODE_ENCRYPTED)
            } else {
                PdfOpen.Failed(PdfError.CODE_STRUCTURE)
            }
        }
        return try {
            val count = session.lock.withLock { session.renderer.pageCount }
            if (count <= 0) {
                PdfOpen.Failed(PdfError.CODE_NO_PAGES)
            } else {
                PdfOpen.Opened(count)
            }
        } catch (error: Throwable) {
            Logger.e(TAG, "cannot read ${file.absolutePath}", error)
            PdfOpen.Failed(PdfError.CODE_STRUCTURE, error)
        }
    }

    /**
     * The number of pages of the document, 0 when it cannot be opened.
     */
    fun pageCount(file: File): Int {
        val session = sessionOf(file) ?: return 0
        return try {
            session.lock.withLock { session.renderer.pageCount }
        } catch (error: Throwable) {
            Logger.e(TAG, "cannot read the page count of ${file.absolutePath}", error)
            0
        }
    }

    /**
     * True when the document announces an /Encrypt entry.
     *
     * Only the head and the tail of the file are read, the document itself is
     * never loaded into memory. The entry is looked for in its indirect form
     * (`/Encrypt 12 0 R`), so a document that merely talks about PDF
     * encryption is not mistaken for an encrypted one.
     */
    fun isEncrypted(file: File): Boolean = runCatching {
        val length = file.length()
        if (length <= 0) return false
        val probe = min(length, ENCRYPT_PROBE).toInt()
        val head = ByteArray(probe)
        val tail = ByteArray(probe)
        RandomAccessFile(file, "r").use { raf ->
            raf.readFully(head)
            raf.seek(length - probe)
            raf.readFully(tail)
        }
        val text = String(head, Charsets.ISO_8859_1) + String(tail, Charsets.ISO_8859_1)
        ENCRYPT_ENTRY.containsMatchIn(text)
    }.getOrDefault(false)

    // ------------------------------------------------------------------ rendering

    /**
     * Renders a page of the document.
     *
     * The bitmap never grows beyond the pixel budget of the engine, so a page
     * of any size can be shown on any device.
     */
    fun renderPage(file: File, pageIndex: Int, targetWidth: Int = RENDER_WIDTH): RenderedPage? {
        val session = sessionOf(file) ?: return null
        return try {
            session.lock.withLock {
                val renderer = session.renderer
                if (pageIndex !in 0 until renderer.pageCount) return null
                renderer.openPage(pageIndex).use { page ->
                    val size = fitSize(page.width, page.height, targetWidth)
                    val bitmap = createBitmap(size.first, size.second) ?: return null
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    RenderedPage(bitmap, page.width, page.height)
                }
            }
        } catch (error: Throwable) {
            Logger.e(TAG, "cannot render page $pageIndex of ${file.absolutePath}", error)
            null
        }
    }

    /**
     * Renders a raster image (png/jpg) as a single "page" so the reader can open
     * the image documents that live in the sandbox as well.
     */
    fun renderImage(file: File, targetWidth: Int = RENDER_WIDTH): RenderedPage? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, targetWidth)
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
            if (decoded == null) {
                null
            } else {
                val scale = targetWidth.toFloat() / decoded.width
                val width = max(1, (decoded.width * scale).roundToInt())
                val height = max(1, (decoded.height * scale).roundToInt())
                val scaled = Bitmap.createScaledBitmap(decoded, width, height, true)
                if (scaled != decoded) decoded.recycle()
                RenderedPage(scaled, 595, max(1, (595 * height.toFloat() / width).toInt()))
            }
        }
    } catch (error: Throwable) {
        Logger.e(TAG, "cannot render the image ${file.absolutePath}", error)
        null
    }

    fun renderThumbnail(file: File, pageIndex: Int, targetWidth: Int = 240): Bitmap? =
        renderPage(file, pageIndex, targetWidth)?.bitmap

    /**
     * The size of the bitmap a page is rendered into.
     *
     * The page is scaled to the wanted width and shrunk further when the
     * result would use more memory than a page is allowed to.
     */
    private fun fitSize(pageWidth: Int, pageHeight: Int, targetWidth: Int): Pair<Int, Int> {
        val wanted = targetWidth.coerceIn(1, MAX_PAGE_WIDTH)
        val scale = wanted.toFloat() / pageWidth.coerceAtLeast(1)
        var width = max(1, (pageWidth * scale).roundToInt())
        var height = max(1, (pageHeight * scale).roundToInt())
        val pixels = width.toLong() * height.toLong()
        if (pixels > MAX_PAGE_PIXELS) {
            val shrink = sqrt(MAX_PAGE_PIXELS.toDouble() / pixels.toDouble())
            width = max(1, (width * shrink).toInt())
            height = max(1, (height * shrink).toInt())
        }
        return width to height
    }

    /**
     * A bitmap of the wanted size, null when the heap cannot hold it.
     */
    private fun createBitmap(width: Int, height: Int): Bitmap? = try {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    } catch (error: OutOfMemoryError) {
        Logger.e(TAG, "out of memory for a ${width}x$height page")
        null
    }

    /**
     * The power of two that brings the image close to the wanted width.
     */
    private fun sampleSize(width: Int, targetWidth: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= targetWidth && sample < 64) sample *= 2
        return sample
    }

    // ------------------------------------------------------------------ signature

    /**
     * Bakes a signature/stamp bitmap into the page and writes a new document.
     *
     * The document is written page by page, so a big document does not need
     * the memory of all of its pages at the same time.
     */
    fun flattenOverlay(
        source: File,
        out: File,
        pageIndex: Int,
        overlay: Bitmap,
        bounds: RectF,
    ): Result<Long> = runCatching {
        val total = pageCount(source)
        if (total <= 0) throw PdfError.UnreadableSource(source.absolutePath)
        val pages = ArrayList<PdfPictureWriter.Source>()
        for (pageNo in 0 until total) {
            val marked = pageNo == pageIndex
            pages += PdfPictureWriter.Source.ofPage(
                source,
                pageNo,
                if (marked) overlay else null,
                if (marked) bounds else null,
            )
        }
        out.parentFile?.let { if (!it.exists()) it.mkdirs() }
        FileOutputStream(out).use { stream ->
            PdfPictureWriter(stream).write(pages)
        }
        out.length()
    }

    // ------------------------------------------------------------------ files

    /** True when the file starts with the PDF magic. */
    fun isPdf(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(5)
            if (input.read(header) != header.size) return false
            String(header, Charsets.ISO_8859_1) == "%PDF-"
        }
    }.getOrDefault(false)

    /** Copies a file, used by the tools that produce a new document. */
    fun copyTo(source: File, target: File): Boolean = runCatching {
        target.parentFile?.let { if (!it.exists()) it.mkdirs() }
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        true
    }.getOrDefault(false)

    // ------------------------------------------------------------------ processing

    /**
     * Concatenates documents into a new one.
     *
     * The work runs on a worker thread, see [PdfProcessor].
     */
    fun merge(
        sources: List<File>,
        out: File,
        insertBlankOnOdd: Boolean = false,
        onProgress: (PdfProgress) -> Unit = {},
        onResult: (Result<Long>) -> Unit,
    ): PdfJob = PdfProcessor.merge(sources, out, insertBlankOnOdd, onProgress, onResult)

    /** Writes every page range into its own document. */
    fun split(
        source: File,
        outDir: File,
        ranges: List<IntRange>,
        onProgress: (PdfProgress) -> Unit = {},
        onResult: (Result<List<File>>) -> Unit,
    ): PdfJob = PdfProcessor.split(source, outDir, ranges, onProgress, onResult)

    /** Writes every page into its own document. */
    fun splitEveryPage(
        source: File,
        outDir: File,
        onProgress: (PdfProgress) -> Unit = {},
        onResult: (Result<List<File>>) -> Unit,
    ): PdfJob = PdfProcessor.splitEveryPage(source, outDir, onProgress, onResult)

    /** Recompresses the streams of a document. */
    fun compress(
        source: File,
        out: File,
        quality: Int,
        onProgress: (PdfProgress) -> Unit = {},
        onResult: (Result<CompressResult>) -> Unit,
    ): PdfJob = PdfProcessor.compress(source, out, quality, onProgress, onResult)

    /** Places images into a new document. */
    fun imagesToPdf(
        images: List<File>,
        out: File,
        layout: ImageLayout = ImageLayout.Single,
        marginMm: Int = 10,
        landscape: Boolean = false,
        onProgress: (PdfProgress) -> Unit = {},
        onResult: (Result<Long>) -> Unit,
    ): PdfJob = PdfProcessor.imagesToPdf(images, out, layout, marginMm, landscape, onProgress, onResult)

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

    /** The indirect reference of an /Encrypt entry. */
    private val ENCRYPT_ENTRY = Regex("/Encrypt\\s+\\d+\\s+\\d+\\s+R")
}
