package com.peng.pdfreader.feature.tools.engine

import com.peng.pdfreader.core.pdf.CompressResult
import com.peng.pdfreader.core.pdf.ImageLayout
import com.peng.pdfreader.core.pdf.PdfJob
import com.peng.pdfreader.core.pdf.PdfProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * The single seam between the tool screens and the PDF engine of the core package.
 *
 * Every screen talks to this interface only, so the wiring with the engine
 * lives in exactly one place. The calls are suspending: they never run on the
 * main thread and they never throw, a failure is reported as a failed
 * [Result].
 */
interface ToolEngine {

    /**
     * Merges the given documents into [out].
     *
     * @param onProgress the finished steps and the total number of steps.
     */
    suspend fun merge(
        files: List<File>,
        out: File,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Result<Long>

    /**
     * Writes one document per range of [ranges] into [outDir].
     *
     * The ranges are 1 based and inclusive.
     */
    suspend fun split(
        source: File,
        outDir: File,
        ranges: List<IntRange>,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Result<List<File>>

    /** Writes one document per page of [source] into [outDir]. */
    suspend fun splitEveryPage(
        source: File,
        outDir: File,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Result<List<File>>

    /**
     * Compresses [source] into [out].
     *
     * @param quality JPEG quality of the re-encoded images, 1 .. 100.
     */
    suspend fun compress(
        source: File,
        out: File,
        quality: Int,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Result<CompressResult>

    /** Places the images into a new document. */
    suspend fun imagesToPdf(
        images: List<File>,
        out: File,
        layout: ImageLayout = ImageLayout.Single,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Result<Long>

    /** Parses a page range expression such as "1-3,5,8-10" into its ranges. */
    fun parseRangeExpression(expression: String): List<IntRange>

    /** Parses a page range expression into the page numbers it selects. */
    fun parsePageNumbers(expression: String, total: Int): List<Int>
}

/**
 * The production implementation, backed by the PDF engine of the core package.
 *
 * The engine of the core package reports through callbacks and runs the work on its
 * own worker; this implementation turns that into a suspending call that can
 * be cancelled together with the coroutine of the screen.
 */
class PdfToolEngine : ToolEngine {

    override suspend fun merge(
        files: List<File>,
        out: File,
        onProgress: (Int, Int) -> Unit,
    ): Result<Long> = await { done ->
        PdfProcessor.merge(
            sources = files,
            out = out,
            onProgress = { progress -> onProgress(progress.current, progress.total) },
            onResult = done,
        )
    }

    override suspend fun split(
        source: File,
        outDir: File,
        ranges: List<IntRange>,
        onProgress: (Int, Int) -> Unit,
    ): Result<List<File>> = await { done ->
        PdfProcessor.split(
            source = source,
            outDir = outDir,
            ranges = ranges,
            onProgress = { progress -> onProgress(progress.current, progress.total) },
            onResult = done,
        )
    }

    override suspend fun splitEveryPage(
        source: File,
        outDir: File,
        onProgress: (Int, Int) -> Unit,
    ): Result<List<File>> = await { done ->
        PdfProcessor.splitEveryPage(
            source = source,
            outDir = outDir,
            onProgress = { progress -> onProgress(progress.current, progress.total) },
            onResult = done,
        )
    }

    override suspend fun compress(
        source: File,
        out: File,
        quality: Int,
        onProgress: (Int, Int) -> Unit,
    ): Result<CompressResult> = await { done ->
        PdfProcessor.compress(
            source = source,
            out = out,
            quality = quality,
            onProgress = { progress -> onProgress(progress.current, progress.total) },
            onResult = done,
        )
    }

    override suspend fun imagesToPdf(
        images: List<File>,
        out: File,
        layout: ImageLayout,
        onProgress: (Int, Int) -> Unit,
    ): Result<Long> = await { done ->
        PdfProcessor.imagesToPdf(
            images = images,
            out = out,
            layout = layout,
            onProgress = { progress -> onProgress(progress.current, progress.total) },
            onResult = done,
        )
    }

    override fun parseRangeExpression(expression: String): List<IntRange> =
        PdfProcessor.parseRangeExpression(expression)

    override fun parsePageNumbers(expression: String, total: Int): List<Int> =
        PdfProcessor.parsePageNumbers(expression, total)

    /**
     * Starts the job of the engine and waits for its result.
     */
    private suspend fun <T> await(start: ((Result<T>) -> Unit) -> PdfJob): Result<T> =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { continuation ->
                val job = start { result ->
                    if (continuation.isActive) continuation.resume(result)
                }
                continuation.invokeOnCancellation { job.cancel() }
            }
        }
}

/** Access to the engine implementation used by the screens. */
object ToolEngines {

    /** The engine of the app. */
    var current: ToolEngine = PdfToolEngine()
}
