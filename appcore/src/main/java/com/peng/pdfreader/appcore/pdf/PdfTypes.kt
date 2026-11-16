package com.peng.pdfreader.appcore.pdf

/**
 * Stage a background PDF job is currently in.
 */
enum class PdfStage {
    MERGE,
    SPLIT,
    COMPRESS,
    IMAGES,
}

/**
 * Snapshot of a running PDF job.
 *
 * @param stage what the job is doing right now
 * @param current number of finished steps
 * @param total number of steps the job consists of
 */
data class PdfProgress(
    val stage: PdfStage,
    val current: Int,
    val total: Int,
) {
    /** Completion in percent, always inside 0..100. */
    val percent: Int
        get() = if (total <= 0) 0 else ((current.toLong() * 100L) / total).toInt().coerceIn(0, 100)
}

/**
 * Handle of a background PDF job.
 *
 * The job never runs on the main thread, so cancelling it is always safe.
 */
interface PdfJob {

    /** Requests a cancellation; the job stops at the next safe checkpoint. */
    fun cancel()

    /** True once [cancel] has been called. */
    val isCancelled: Boolean
}

/**
 * Every failure the PDF engine is able to report.
 *
 * The UI never sees a raw exception: the engine always fails with one of these
 * so that the caller can branch on the concrete cause.
 *
 * The messages are **stable error codes**, not copy. The app maps them onto
 * localised strings, which keeps the engine free of any user facing text and
 * of any dependency on the resource system.
 */
sealed class PdfError(code: String, cause: Throwable? = null) : Exception(code, cause) {

    /** The source file cannot be opened or parsed. */
    class UnreadableSource(val path: String, cause: Throwable? = null) :
        PdfError(CODE_UNREADABLE, cause)

    /** The target file cannot be written. */
    class UnwritableTarget(val path: String, cause: Throwable? = null) :
        PdfError(CODE_UNWRITABLE, cause)

    /** The document uses a feature the engine cannot handle. */
    class Unsupported(val reason: String) : PdfError(reason)

    /** The job was cancelled by the user. */
    class Cancelled : PdfError(CODE_CANCELLED)

    companion object {
        /** The source file cannot be read. */
        const val CODE_UNREADABLE = "PDF_UNREADABLE"

        /** The target file cannot be written. */
        const val CODE_UNWRITABLE = "PDF_UNWRITABLE"

        /** The job was cancelled. */
        const val CODE_CANCELLED = "CANCELLED"

        /** The PDF structure cannot be parsed. */
        const val CODE_STRUCTURE = "PDF_STRUCTURE"

        /** The document is encrypted and cannot be processed. */
        const val CODE_ENCRYPTED = "PDF_ENCRYPTED"

        /** There is no file to work on. */
        const val CODE_NO_FILES = "NO_FILES"

        /** There is no page to work on. */
        const val CODE_NO_PAGES = "NO_PAGES"

        /** There is no image to work on. */
        const val CODE_NO_IMAGES = "NO_IMAGES"

        /** The document is too big to be processed on the phone. */
        const val CODE_TOO_LARGE = "PDF_TOO_LARGE"
    }
}

/**
 * Outcome of a compression run.
 *
 * @param originalSize size of the source document
 * @param newSize size of the produced document
 * @param pages number of pages of the document
 * @param recompressedImages number of embedded images that were re-encoded
 * @param keptOriginal true when the recompressed file was not smaller and the
 * original bytes were kept instead
 */
data class CompressResult(
    val originalSize: Long,
    val newSize: Long,
    val pages: Int,
    val recompressedImages: Int,
    val keptOriginal: Boolean = false,
) {
    /** Bytes saved compared to the source document. */
    val savedBytes: Long get() = (originalSize - newSize).coerceAtLeast(0)

    /** Saving in percent, 0 when nothing could be saved. */
    val savedPercent: Int
        get() = if (originalSize <= 0) 0 else ((savedBytes * 100L) / originalSize).toInt().coerceIn(0, 100)
}

/**
 * Grid used when several images share a single page.
 */
data class ImageLayout(
    val columns: Int,
    val rows: Int,
) {
    /** Number of images that fit onto one page. */
    val perPage: Int get() = (columns * rows).coerceAtLeast(1)

    companion object {
        /** One image per page. */
        val Single = ImageLayout(1, 1)
    }
}
