package com.peng.pdfreader.core.pdf

import android.content.Context
import com.peng.pdfreader.core.util.Logger
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.StringWriter

/**
 * The content of a PDF: its text, its outline and its annotations.
 *
 * The pages of a document are drawn by the framework of the system, it is
 * fast and it is exact. The framework cannot read the text of a page, it
 * cannot read the outline of a document and it cannot write an annotation
 * into it - PDFBox does all of that.
 */
object PdfContent {

    private const val TAG = "PdfContent"

    /** The text around a hit of the search. */
    private const val SNIPPET_PADDING = 24

    /** The size of a note, in points. */
    private const val NOTE_SIZE = 24f

    /**
     * The name of the annotations the app writes.
     *
     * The name tells the annotations of the app from the ones of the
     * document, so a second run replaces them instead of doubling them.
     */
    private const val ANNOTATION_NAME = "PdfReader"

    /**
     * The sign that ends a page in the text of the document.
     *
     * The reader of PDFBox writes it behind every page, so the text of the
     * document can be cut into the pages again.
     */
    private const val PAGE_END = "\u000C"

    /** A hit of the search. */
    data class Hit(
        /** The number of the page, it starts at one. */
        val page: Int,
        /** The text around the hit. */
        val snippet: String,
    )

    /**
     * Loads the resources of the engine.
     *
     * The fonts of the standard are part of the resources, they are needed
     * for the text of a document.
     */
    fun init(context: Context) {
        runCatching { PDFBoxResourceLoader.init(context.applicationContext) }
            .onFailure {
                Logger.e(TAG, "the resources of the pdf engine cannot be loaded", it)
            }
    }

    /**
     * The text of every page of the document.
     *
     * The pages that cannot be read are empty, a document without a text is
     * still a document that can be shown.
     */
    fun textByPage(file: File): List<String> = runCatching {
        PDDocument.load(file).use { document ->
            val stripper = PDFTextStripper()
            stripper.setPageEnd(PAGE_END)
            val writer = StringWriter()
            stripper.writeText(document, writer)
            cutIntoPages(writer.toString(), document.numberOfPages)
        }
    }.onFailure {
        Logger.e(TAG, "the text of ${file.name} cannot be read", it)
    }.getOrDefault(emptyList())

    /**
     * Searches the text of the document.
     *
     * @return one hit for every page that carries the word
     */
    fun search(file: File, keyword: String): List<Hit> {
        val needle = keyword.trim()
        if (needle.isEmpty()) return emptyList()
        val lowerNeedle = needle.lowercase()
        val result = ArrayList<Hit>()
        textByPage(file).forEachIndexed { index, text ->
            val position = text.lowercase().indexOf(lowerNeedle)
            if (position < 0) return@forEachIndexed
            result += Hit(index + 1, snippetOf(text, position, needle.length))
        }
        return result
    }

    /**
     * The outline (the table of contents) of the document.
     */
    fun outline(file: File): List<OutlineNode> = runCatching {
        PDDocument.load(file).use { document ->
            val root = document.documentCatalog.documentOutline
            if (root == null) {
                emptyList()
            } else {
                val result = ArrayList<OutlineNode>()
                collect(root.firstChild, 0, document, result)
                result
            }
        }
    }.onFailure {
        Logger.e(TAG, "the outline of ${file.name} cannot be read", it)
    }.getOrDefault(emptyList())

    /**
     * Writes the annotations of the pages into a copy of the document.
     *
     * The annotations of the reader are written into the file, so the
     * document carries them when it is opened with another program.
     *
     * @param source the document that is read
     * @param target the file the document is written into
     * @param annotations the annotations of the pages, the key is the number
     * of the page
     * @return true when the document was written
     */
    fun writeAnnotations(
        source: File,
        target: File,
        annotations: Map<Int, List<PdfAnnotation>>,
    ): Boolean {
        if (annotations.isEmpty()) return false
        return runCatching {
            PDDocument.load(source).use { document ->
                val pages = document.pages
                annotations.forEach { (number, list) ->
                    val index = number - 1
                    if (index < 0 || index >= pages.count) return@forEach
                val page = pages.get(index)
                // The annotations of a former run of the app are replaced, the
                // ones of the document itself stay.
                clearOwnAnnotations(page)
                list.forEach { apply(document, page, it) }
                }
                target.outputStream().use { out -> document.save(out) }
            }
            true
        }.onFailure {
            Logger.e(TAG, "the annotations cannot be written into ${source.name}", it)
        }.getOrDefault(false)
    }

    // ----------------------------------------------------------------- pages

    /** Cuts the text of the document into the pages. */
    private fun cutIntoPages(text: String, pages: Int): List<String> {
        val parts = text.split(PAGE_END)
        val result = ArrayList<String>(pages)
        for (index in 0 until pages) {
            result += parts.getOrNull(index)?.trim().orEmpty()
        }
        return result
    }

    /** The text around a hit of the search. */
    private fun snippetOf(text: String, position: Int, length: Int): String {
        val start = (position - SNIPPET_PADDING).coerceAtLeast(0)
        val end = (position + length + SNIPPET_PADDING).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return prefix + text.substring(start, end).replace('\n', ' ').trim() + suffix
    }

    // --------------------------------------------------------------- outline

    /** Reads the outline of the document, the children of an entry follow it. */
    private fun collect(
        item: PDOutlineItem?,
        level: Int,
        document: PDDocument,
        result: MutableList<OutlineNode>,
    ) {
        var current = item
        while (current != null) {
            val page = runCatching { current.findDestinationPage(document) }.getOrNull()
            val number = page?.let { document.pages.indexOf(it) } ?: -1
            result += OutlineNode(
                title = current.title.orEmpty(),
                page = number + 1,
                level = level,
            )
            collect(current.firstChild, level + 1, document, result)
            current = current.nextSibling
        }
    }

    // ----------------------------------------------------------- annotations

    /** Writes one annotation into a page. */
    private fun apply(document: PDDocument, page: PDPage, annotation: PdfAnnotation) {
        val box = page.mediaBox
        val pageWidth = box.width
        val pageHeight = box.height
        val left = box.lowerLeftX + annotation.left * pageWidth
        val right = box.lowerLeftX + annotation.right * pageWidth
        // The reader measures the page from the top, the pdf measures it from
        // the bottom.
        val bottom = box.lowerLeftY + (1f - annotation.bottom) * pageHeight
        val top = box.lowerLeftY + (1f - annotation.top) * pageHeight

        when (annotation.kind) {
            PdfAnnotationKind.HIGHLIGHT,
            PdfAnnotationKind.UNDERLINE,
            PdfAnnotationKind.STRIKETHROUGH,
            -> applyMarkup(document, page, annotation, left, bottom, right, top)

            PdfAnnotationKind.NOTE -> applyNote(page, annotation, left, top)
            PdfAnnotationKind.DRAW,
            PdfAnnotationKind.SIGNATURE,
            -> applyInk(document, page, annotation, box, pageWidth, pageHeight)
        }
    }

    /** Writes a highlight, an underline or a line through a text. */
    private fun applyMarkup(
        document: PDDocument,
        page: PDPage,
        annotation: PdfAnnotation,
        left: Float,
        bottom: Float,
        right: Float,
        top: Float,
    ) {
        val subtype = when (annotation.kind) {
            PdfAnnotationKind.UNDERLINE -> PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE
            PdfAnnotationKind.STRIKETHROUGH -> PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT
            else -> PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT
        }
        val markup = PDAnnotationTextMarkup(subtype)
        markup.setRectangle(PDRectangle(left, bottom, right, top))
        // The points of the corners of the area: the left ones first, the
        // right ones after them.
        markup.setQuadPoints(
            floatArrayOf(
                left, top, right, top,
                left, bottom, right, bottom,
            ),
        )
        markup.setColor(colourOf(annotation.colour))
        markup.setContents(annotation.text)
        markup.setAnnotationName(ANNOTATION_NAME)
        page.setAnnotations(page.annotations + markup)
        // The appearance of the mark is drawn by the engine, so a reader
        // without the support for the annotation still shows it.
        runCatching { markup.constructAppearances(document) }
    }

    /** Writes a note that is pinned to the page. */
    private fun applyNote(page: PDPage, annotation: PdfAnnotation, left: Float, top: Float) {
        val note = PDAnnotationText()
        val size = NOTE_SIZE
        note.setRectangle(PDRectangle(left, top - size, left + size, top))
        note.setContents(annotation.text)
        note.setAnnotationName(ANNOTATION_NAME)
        page.setAnnotations(page.annotations + note)
    }

    /**
     * Removes the annotations of a former run of the app.
     *
     * The annotations of the document itself stay, only the ones the app has
     * written are dropped, so a second run does not write them twice.
     */
    private fun clearOwnAnnotations(page: PDPage) {
        val annotations = page.annotations
        if (annotations.none { it.annotationName == ANNOTATION_NAME }) return
        page.setAnnotations(annotations.filter { it.annotationName != ANNOTATION_NAME })
    }

    /**
     * Writes a free hand mark.
     *
     * The strokes are drawn into the page itself, the way a signature is
     * drawn on a printed page. The text of the page stays readable, the mark
     * is a part of the page from then on.
     */
    private fun applyInk(
        document: PDDocument,
        page: PDPage,
        annotation: PdfAnnotation,
        box: PDRectangle,
        pageWidth: Float,
        pageHeight: Float,
    ) {
        appendStrokes(document, page, annotation.strokes, annotation.colour, annotation.width, box, pageWidth, pageHeight)
    }

    /**
     * Writes strokes into a copy of the document.
     *
     * The strokes are drawn into the page itself, so the text of the page
     * stays readable - a document that is written in this way keeps its text
     * layer, unlike a document that is drawn again as a picture.
     *
     * @param source the document that is read
     * @param target the file the document is written into
     * @param pageNumber the number of the page, it starts at one
     * @param strokes the strokes, x and y in the fractions of the page
     * @param colour the colour of the strokes
     * @param width the width of the strokes, as a fraction of the width of
     * the page
     * @return true when the document was written
     */
    fun writeStrokes(
        source: File,
        target: File,
        pageNumber: Int,
        strokes: List<FloatArray>,
        colour: Int,
        width: Float,
    ): Boolean {
        if (strokes.isEmpty()) return false
        return runCatching {
            PDDocument.load(source).use { document ->
                val index = pageNumber - 1
                if (index < 0 || index >= document.pages.count) return@use false
                val page = document.pages.get(index)
                val box = page.mediaBox
                appendStrokes(
                    document,
                    page,
                    strokes,
                    colour,
                    width,
                    box,
                    box.width,
                    box.height,
                )
                target.outputStream().use { out -> document.save(out) }
            }
            true
        }.onFailure {
            Logger.e(TAG, "the strokes cannot be written into ${source.name}", it)
        }.getOrDefault(false)
    }

    /** Draws the strokes into the content of the page. */
    private fun appendStrokes(
        document: PDDocument,
        page: PDPage,
        strokes: List<FloatArray>,
        colour: Int,
        width: Float,
        box: PDRectangle,
        pageWidth: Float,
        pageHeight: Float,
    ) {
        val lines = strokes.filter { it.size >= 4 }
        if (lines.isEmpty()) return
        val red = ((colour shr 16) and 0xff) / 255f
        val green = ((colour shr 8) and 0xff) / 255f
        val blue = (colour and 0xff) / 255f
        val content = PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.APPEND,
            true,
            true,
        )
        content.use {
            it.setStrokingColor(red, green, blue)
            it.setLineWidth((width * pageWidth).coerceAtLeast(0.5f))
            it.setLineCapStyle(1)
            it.setLineJoinStyle(1)
            lines.forEach { stroke ->
                var index = 0
                while (index + 3 < stroke.size) {
                    val x = box.lowerLeftX + stroke[index] * pageWidth
                    val y = box.lowerLeftY + (1f - stroke[index + 1]) * pageHeight
                    if (index == 0) {
                        it.moveTo(x, y)
                    } else {
                        it.lineTo(x, y)
                    }
                    index += 2
                }
            }
            it.stroke()
        }
    }

    /** The colour of an annotation, as the pdf writes it. */
    private fun colourOf(colour: Int): PDColor = PDColor(
        floatArrayOf(
            ((colour shr 16) and 0xff) / 255f,
            ((colour shr 8) and 0xff) / 255f,
            (colour and 0xff) / 255f,
        ),
        PDDeviceRGB.INSTANCE,
    )
}
