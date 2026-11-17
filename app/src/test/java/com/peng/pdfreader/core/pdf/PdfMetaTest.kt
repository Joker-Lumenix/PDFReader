package com.peng.pdfreader.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The metadata reader of the PDF engine.
 *
 * The reader scans the objects of the file in chunks, so a big document does
 * not have to be loaded into the memory as a whole.
 */
class PdfMetaTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `the outline of a document is read`() {
        val file = folder.newFile("outline.pdf")
        file.writeBytes(pdfWithOutline().toByteArray(Charsets.ISO_8859_1))

        val outline = PdfMeta.outline(file)

        assertEquals(1, outline.size)
        assertEquals("Chapter 1", outline.first().title)
        assertEquals(1, outline.first().page)
    }

    @Test
    fun `the text of the pages is read`() {
        val file = folder.newFile("text.pdf")
        file.writeBytes(pdfWithOutline().toByteArray(Charsets.ISO_8859_1))

        val pages = PdfMeta.textByPage(file)

        assertEquals(1, pages.size)
        assertTrue("expected the text of the page, got ${pages.first()}", pages.first().contains("Hello"))
    }

    @Test
    fun `a file that is no pdf has no outline`() {
        val file = folder.newFile("broken.pdf")
        file.writeBytes("not a pdf at all".toByteArray())

        assertTrue(PdfMeta.outline(file).isEmpty())
        assertTrue(PdfMeta.textByPage(file).isEmpty())
    }

    /** A small document with one page and one outline entry. */
    private fun pdfWithOutline(): String = buildString {
        append("%PDF-1.4\n")
        append("1 0 obj\n<< /Type /Catalog /Pages 2 0 R /Outlines 5 0 R >>\nendobj\n")
        append("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")
        append("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] ")
        append("/Resources << /Font << /F1 7 0 R >> >> /Contents 4 0 R >>\nendobj\n")
        val content = "BT /F1 24 Tf 72 700 Td (Hello world) Tj ET"
        append("4 0 obj\n<< /Length ${content.length} >>\nstream\n$content\nendstream\nendobj\n")
        append("5 0 obj\n<< /Type /Outlines /First 6 0 R /Last 6 0 R /Count 1 >>\nendobj\n")
        append("6 0 obj\n<< /Title (Chapter 1) /Parent 5 0 R /Dest [3 0 R /Fit] >>\nendobj\n")
        append("7 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n")
        append("trailer\n<< /Size 8 /Root 1 0 R >>\n%%EOF\n")
    }
}
