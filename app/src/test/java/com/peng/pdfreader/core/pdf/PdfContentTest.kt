package com.peng.pdfreader.core.pdf

import com.peng.pdfreader.data.MiniPdf
import com.tom_roush.pdfbox.pdmodel.PDDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The content of a PDF against a document the app writes itself.
 *
 * The document is a plain PDF with the standard Helvetica font.
 *
 * The text of a document is read by the unit test of the device
 * ([PdfContentInstrumentedTest]): the fonts of the standard live in the
 * assets of the app, a unit test of the virtual machine has no access to
 * them.
 */
class PdfContentTest {

    /** Writes a document with two pages and a known text. */
    private fun document(): File {
        val file = File.createTempFile("content-", ".pdf")
        file.deleteOnExit()
        val pdf = MiniPdf()
        pdf.addPage(
            listOf(
                MiniPdf.Line("Hello PDF reader", 18f, 72f, 700f),
                MiniPdf.Line("the second line of the page", 12f, 72f, 660f),
            ),
        )
        pdf.addPage(listOf(MiniPdf.Line("the last page", 12f, 72f, 700f)))
        pdf.writeTo(file)
        return file
    }

    /**
     * The annotations are written into the document.
     *
     * The document that is written carries the annotations, the ones of a
     * former run of the app are replaced.
     */
    @Test
    fun theAnnotationsAreWrittenIntoTheDocument() {
        val source = document()
        val target = File.createTempFile("annotated-", ".pdf")
        target.deleteOnExit()

        val annotations = mapOf(
            1 to listOf(
                PdfAnnotation(
                    kind = PdfAnnotationKind.HIGHLIGHT,
                    colour = 0xfff9d925.toInt(),
                    left = 0.1f,
                    top = 0.1f,
                    right = 0.5f,
                    bottom = 0.14f,
                ),
                PdfAnnotation(
                    kind = PdfAnnotationKind.NOTE,
                    colour = 0xfffb4141.toInt(),
                    left = 0.88f,
                    top = 0.02f,
                    right = 0.94f,
                    bottom = 0.06f,
                    text = "a note",
                ),
            ),
        )
        assertTrue(
            "the annotations should be written",
            PdfContent.writeAnnotations(source, target, annotations),
        )
        assertTrue("the document should be there", target.exists())
        assertTrue(
            "the document should be bigger than the empty one",
            target.length() > source.length(),
        )

        // A second run replaces the annotations instead of doubling them.
        val second = File.createTempFile("annotated-", ".pdf")
        second.deleteOnExit()
        assertTrue(
            "the annotations should be written again",
            PdfContent.writeAnnotations(target, second, annotations),
        )
        assertEquals(
            "the number of the annotations of the first page",
            countAnnotations(target, 1),
            countAnnotations(second, 1),
        )
    }

    /**
     * The strokes are written into the page of the document.
     *
     * The document keeps its pages, the strokes are drawn into the page
     * itself.
     */
    @Test
    fun theStrokesAreWrittenIntoTheDocument() {
        val source = document()
        val target = File.createTempFile("signed-", ".pdf")
        target.deleteOnExit()
        val strokes = listOf(
            floatArrayOf(0.2f, 0.5f, 0.3f, 0.55f, 0.4f, 0.5f),
        )
        assertTrue(
            "the strokes should be written",
            PdfContent.writeStrokes(source, target, 1, strokes, 0xfb4141.toInt(), 0.01f),
        )
        PDDocument.load(target).use { document ->
            assertEquals("the number of the pages", 2, document.numberOfPages)
        }
    }

    /** The number of the annotations of a page. */
    private fun countAnnotations(file: File, pageNumber: Int): Int {
        PDDocument.load(file).use { document ->
            return document.pages.get(pageNumber - 1).annotations.size
        }
    }
}
