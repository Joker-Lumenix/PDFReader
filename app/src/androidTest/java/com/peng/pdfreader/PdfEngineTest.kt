package com.peng.pdfreader

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.peng.pdfreader.core.pdf.PdfEngine
import com.peng.pdfreader.data.DocumentRepository
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The PDF engine against a real document of the sandbox.
 */
@RunWith(AndroidJUnit4::class)
class PdfEngineTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun samplePdf(): File {
        val repository = DocumentRepository.get(context)
        val document = repository.all().firstOrNull { it.name.endsWith(".pdf") }
            ?: error("the sandbox holds no PDF")
        return repository.fileOf(document)
    }

    @Test
    fun aPdfReportsItsPageCount() {
        val file = samplePdf()
        assertTrue("the PDF should have pages", PdfEngine.pageCount(file) > 0)
    }

    @Test
    fun aPageIsRendered() {
        val file = samplePdf()
        val page = PdfEngine.renderPage(file, 0)
        assertNotNull("the first page should render", page)
        assertTrue(page!!.bitmap.width > 0)
        assertTrue(page.bitmap.height > 0)
    }

    @Test
    fun aThumbnailIsRendered() {
        val file = samplePdf()
        val thumbnail = PdfEngine.renderThumbnail(file, 0, 120)
        assertNotNull(thumbnail)
        assertTrue(thumbnail!!.width <= 120)
    }

    @Test
    fun aPageOutOfRangeDoesNotRender() {
        val file = samplePdf()
        val page = PdfEngine.renderPage(file, 9_999)
        assertTrue("an invalid page must not render", page == null)
    }

    @Test
    fun theTextOfAPdfCanBeSearched() {
        val file = samplePdf()
        val pages = PdfEngine.extractTextByPage(file)
        assertTrue("the PDF should have text", pages.isNotEmpty())
        // the first page of a document does not always carry text, the keyword
        // is taken from a page that does
        val text = pages.firstOrNull { it.isNotBlank() }
        assertNotNull("no page with text was found", text)
        val keyword = text!!.trim().take(2)
        assertTrue("the keyword must not be empty", keyword.isNotBlank())
        val hits = PdfEngine.searchText(file, keyword)
        assertTrue("the keyword should be found", hits.isNotEmpty())
    }
}
