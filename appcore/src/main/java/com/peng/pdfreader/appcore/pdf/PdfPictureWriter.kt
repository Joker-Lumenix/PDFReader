package com.peng.pdfreader.appcore.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.Locale

/**
 * Writes a PDF whose pages are pictures of other documents.
 *
 * The framework writer (android.graphics.pdf.PdfDocument) keeps every page in
 * the native memory until the whole document is written, so a document with
 * hundreds of pages does not fit into the memory of a phone - a merge of a few
 * hundred pages kills the app. This writer puts every page into the file as
 * soon as it is rendered, so only one page is held at a time and a document of
 * any size can be written that way.
 *
 * The pages are stored as JPEG: that is the format the platform can produce
 * without another dependency, it keeps the file small and it stays readable.
 */
internal class PdfPictureWriter(private val out: OutputStream) {

    /**
     * A page of the document that is written.
     *
     * A page is either a page of another document or a picture that the caller
     * drew itself.
     */
    class Source internal constructor(
        internal val file: File?,
        internal val index: Int,
        internal val overlay: Bitmap?,
        internal val overlayBounds: RectF?,
        internal val drawn: Bitmap?,
        internal val drawnWidthPt: Int,
        internal val drawnHeightPt: Int,
    ) {

        companion object {

            /**
             * A page of another document.
             *
             * @param overlay a picture that is drawn on top of the page
             * @param overlayBounds where the overlay is placed, in points
             */
            fun ofPage(
                file: File,
                index: Int,
                overlay: Bitmap? = null,
                overlayBounds: RectF? = null,
            ): Source = Source(file, index, overlay, overlayBounds, null, 0, 0)

            /**
             * A page that the caller drew itself, used by the image tool.
             */
            fun ofPicture(picture: Bitmap, widthPt: Int, heightPt: Int): Source =
                Source(null, 0, null, null, picture, widthPt, heightPt)

            /** A page without any content. */
            fun blank(): Source = Source(null, 0, null, null, null, 0, 0)
        }
    }

    /** The offsets of the written objects, they build the cross reference. */
    private val offsets = HashMap<Int, Long>()

    /** How many bytes were written so far. */
    private var position = 0L

    /**
     * Writes the pages into the file.
     *
     * @param pages the pages of the document, in the order they appear in it
     * @param onPage raised for every page that was written
     */
    fun write(pages: List<Source>, onPage: (Int) -> Unit = {}) {
        val count = pages.size
        val pageIds = IntArray(count) { FIRST_PAGE_ID + it * OBJECTS_PER_PAGE }

        writeText("%PDF-1.4\n")
        writeBytes(byteArrayOf(0x25, 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), 0x0A))

        offsets[CATALOG_ID] = position
        writeText("$CATALOG_ID 0 obj\n<< /Type /Catalog /Pages $TREE_ID 0 R >>\nendobj\n")

        pages.forEachIndexed { index, source ->
            val pageId = pageIds[index]
            val contentId = pageId + 1
            val imageId = pageId + 2
            val picture = render(source)

            offsets[pageId] = position
            writeText(
                "$pageId 0 obj\n<< /Type /Page /Parent $TREE_ID 0 R " +
                    "/MediaBox [0 0 ${picture.widthPt} ${picture.heightPt}] " +
                    "/Resources << /XObject << /Im0 $imageId 0 R >> /ProcSet [/PDF /ImageC] >> " +
                    "/Contents $contentId 0 R >>\nendobj\n",
            )

            // The picture fills the whole page, so the unit square is scaled up
            // to the size of the page.
            val content = "q ${picture.widthPt} 0 0 ${picture.heightPt} 0 0 cm /Im0 Do Q\n"
            offsets[contentId] = position
            writeText(
                "$contentId 0 obj\n<< /Length ${content.toByteArray(Charsets.ISO_8859_1).size} >>\n" +
                    "stream\n",
            )
            writeText(content)
            writeText("endstream\nendobj\n")

            offsets[imageId] = position
            writeText(
                "$imageId 0 obj\n<< /Type /XObject /Subtype /Image " +
                    "/Width ${picture.width} /Height ${picture.height} " +
                    "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode " +
                    "/Length ${picture.jpeg.size} >>\nstream\n",
            )
            writeBytes(picture.jpeg)
            writeText("\nendstream\nendobj\n")

            onPage(index + 1)
        }

        // The page tree is written at the end: it carries the numbers of the
        // pages, which are known from the start.
        offsets[TREE_ID] = position
        writeText("$TREE_ID 0 obj\n<< /Type /Pages /Kids [")
        pageIds.forEachIndexed { index, id ->
            if (index > 0) writeText(" ")
            writeText("$id 0 R")
        }
        writeText("] /Count $count >>\nendobj\n")

        writeCrossReference()
    }

    // ------------------------------------------------------------------ pages

    /**
     * The picture of one page.
     */
    private class Picture(
        val width: Int,
        val height: Int,
        val widthPt: Int,
        val heightPt: Int,
        val jpeg: ByteArray,
    )

    /**
     * The picture of a page.
     *
     * A page that cannot be rendered becomes a blank one, so the document
     * keeps the number of pages it was promised.
     */
    private fun render(source: Source): Picture {
        val drawn = source.drawn
        if (drawn != null) {
            val jpeg = compress(drawn)
            return Picture(
                width = drawn.width,
                height = drawn.height,
                widthPt = source.drawnWidthPt,
                heightPt = source.drawnHeightPt,
                jpeg = jpeg,
            )
        }
        val file = source.file ?: return blank()
        val rendered = PdfEngine.renderPage(file, source.index, RENDER_WIDTH) ?: return blank()
        val bitmap = rendered.bitmap
        try {
            drawOverlay(source, bitmap)
            val jpeg = compress(bitmap)
            if (jpeg.isEmpty()) return blank()
            return Picture(
                width = bitmap.width,
                height = bitmap.height,
                widthPt = rendered.pageWidthPt,
                heightPt = rendered.pageHeightPt,
                jpeg = jpeg,
            )
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /**
     * Draws the overlay of a page onto its picture.
     *
     * The rectangle of the overlay is given for a page of 595 points wide -
     * the coordinate space the annotation editor uses - so it is scaled with
     * the picture of the page.
     */
    private fun drawOverlay(source: Source, bitmap: Bitmap) {
        val overlay = source.overlay ?: return
        val bounds = source.overlayBounds ?: return
        val scale = bitmap.width.toFloat() / OVERLAY_PAGE_WIDTH_PT.toFloat()
        Canvas(bitmap).drawBitmap(
            overlay,
            null,
            RectF(
                bounds.left * scale,
                bounds.top * scale,
                bounds.right * scale,
                bounds.bottom * scale,
            ),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
    }

    /**
     * The picture of a blank page.
     */
    private fun blank(): Picture {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val jpeg = compress(bitmap)
        bitmap.recycle()
        return Picture(1, 1, A4_WIDTH_PT, A4_HEIGHT_PT, jpeg)
    }

    // ------------------------------------------------------------------ output

    /**
     * The picture of a page as JPEG.
     */
    private fun compress(bitmap: Bitmap): ByteArray {
        val buffer = ByteArrayOutputStream(64 * 1024)
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, buffer)
        return buffer.toByteArray()
    }

    /**
     * Writes the cross reference table and the trailer.
     */
    private fun writeCrossReference() {
        val size = (offsets.keys.maxOrNull() ?: 0) + 1
        val xrefPosition = position
        writeText("xref\n")
        writeText("0 $size\n")
        writeText("0000000000 65535 f \n")
        for (id in 1 until size) {
            writeText(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[id] ?: 0L))
        }
        writeText(
            "trailer\n<< /Size $size /Root $CATALOG_ID 0 R >>\nstartxref\n$xrefPosition\n%%EOF\n",
        )
    }

    private fun writeText(text: String) {
        val data = text.toByteArray(Charsets.ISO_8859_1)
        out.write(data)
        position += data.size
    }

    private fun writeBytes(data: ByteArray) {
        out.write(data)
        position += data.size
    }

    companion object {
        /** The quality of the pictures, high enough to keep the text readable. */
        private const val JPEG_QUALITY = 92

        /** The width a page is rendered with. */
        private const val RENDER_WIDTH = 1240

        /** The number of the catalog, of the page tree and of the first page. */
        private const val CATALOG_ID = 1
        private const val TREE_ID = 2
        private const val FIRST_PAGE_ID = 3

        /** Every page needs three objects: the page, its content and its picture. */
        private const val OBJECTS_PER_PAGE = 3

        /** The size of a page without a known format, in points. */
        private const val A4_WIDTH_PT = 595
        private const val A4_HEIGHT_PT = 842

        /** The width of the page the overlay of an annotation is placed in. */
        private const val OVERLAY_PAGE_WIDTH_PT = 595
    }
}
