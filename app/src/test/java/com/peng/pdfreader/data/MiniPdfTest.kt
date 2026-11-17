package com.peng.pdfreader.data

import com.peng.pdfreader.core.pdf.PdfEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The demo PDF of the app has to carry readable text, otherwise the search of
 * the reader would never find anything in it.
 */
class MiniPdfTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun writeSample(): java.io.File {
        val pdf = MiniPdf()
        pdf.addPage(
            listOf(
                MiniPdf.Line("Employee Handbook", 22f, 48f, 750f),
                MiniPdf.Line("Chapter 1 General Provisions", 17f, 48f, 700f),
            ),
        )
        pdf.addPage(
            listOf(MiniPdf.Line("Chapter 3 Reimbursement Process", 17f, 48f, 750f)),
        )
        val file = folder.newFile("demo.pdf")
        pdf.writeTo(file)
        return file
    }

    @Test
    fun theTextOfTheGeneratedPdfCanBeExtracted() {
        val file = writeSample()
        val pages = PdfEngine.extractTextByPage(file)
        assertEquals(2, pages.size)
        assertTrue("the first page should carry text: $pages", pages[0].isNotBlank())
        assertTrue("the text should be readable: ${pages[0]}", pages[0].contains("Employee Handbook"))
        assertTrue("the second page should carry text: $pages", pages[1].contains("Reimbursement"))
    }

    @Test
    fun theTextOfTheGeneratedPdfCanBeSearched() {
        val file = writeSample()
        val hits = PdfEngine.searchText(file, "Handbook")
        assertEquals("the keyword should be found once", 1, hits.size)
        assertEquals(1, hits.first().first)
    }

    @Test
    fun anUnknownKeywordIsNotFound() {
        val file = writeSample()
        assertTrue(PdfEngine.searchText(file, "nothing here").isEmpty())
    }
}
