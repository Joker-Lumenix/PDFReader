package com.peng.pdfreader.core.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.peng.pdfreader.data.MiniPdf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The text of a PDF on the device.
 *
 * The fonts of the standard live in the assets of the app, so the text of a
 * document can only be read on the device.
 */
@RunWith(AndroidJUnit4::class)
class PdfContentInstrumentedTest {

    /** The resources of the engine need the context of the app. */
    private fun prepare() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PdfContent.init(context)
    }

    /** Writes a document with two pages and a known text. */
    private fun document(): File {
        val file = File.createTempFile("content-", ".pdf")
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
     * The text of a page is read.
     */
    @Test
    fun theTextOfTheDocumentIsRead() {
        prepare()
        val pages = PdfContent.textByPage(document())
        assertEquals("the number of the pages", 2, pages.size)
        assertTrue(
            "the text of the first page should carry the word, was ${pages.getOrNull(0)}",
            pages.getOrNull(0)?.contains("Hello PDF reader") == true,
        )
        assertTrue(
            "the text of the last page should carry the word",
            pages.getOrNull(1)?.contains("the last page") == true,
        )
    }

    /**
     * A word is found in the document.
     */
    @Test
    fun aWordIsFoundInTheDocument() {
        prepare()
        val hits = PdfContent.search(document(), "last page")
        assertEquals("the number of the hits", 1, hits.size)
        assertEquals("the page of the hit", 2, hits.getOrNull(0)?.page)
    }

    /**
     * A word that is not in the document is not found.
     */
    @Test
    fun aWordThatIsNotInTheDocumentIsNotFound() {
        prepare()
        assertTrue("there should be no hit", PdfContent.search(document(), "a needle").isEmpty())
    }
}
