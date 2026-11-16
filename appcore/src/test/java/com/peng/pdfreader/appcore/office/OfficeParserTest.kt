package com.peng.pdfreader.appcore.office

import com.peng.pdfreader.appcore.office.docx.DocxBlock
import com.peng.pdfreader.appcore.office.docx.DocxParser
import com.peng.pdfreader.appcore.office.pptx.PptxParser
import com.peng.pdfreader.appcore.office.pptx.PptxShape
import com.peng.pdfreader.appcore.office.xlsx.XlsxParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Office parsers against the real sample documents.
 *
 * The samples are the ones the app ships, so a broken parser is caught here
 * before it ever reaches a screen.
 */
class OfficeParserTest {

    private fun sample(name: String): File {
        val resource = javaClass.classLoader?.getResource("seed/$name")
            ?: error("the sample $name is missing")
        val target = File.createTempFile("sample-", ".tmp")
        target.deleteOnExit()
        File(resource.toURI()).copyTo(target, overwrite = true)
        return target
    }

    @Test
    fun aWordDocumentIsParsed() {
        val pkg = OoxmlPackage.open(sample(SAMPLE_DOCX)) ?: error("package not readable")
        val document = DocxParser.parse(pkg)
        pkg.close()

        assertNotNull("the document should parse", document)
        val blocks = document!!.blocks
        assertTrue("the document should have blocks", blocks.isNotEmpty())
        assertTrue(
            "the document should have headings",
            blocks.filterIsInstance<DocxBlock.Paragraph>().any { it.headingLevel == 1 },
        )
        assertTrue(
            "the document should have a table",
            blocks.any { it is DocxBlock.Table },
        )
    }

    @Test
    fun aSpreadsheetIsParsed() {
        val pkg = OoxmlPackage.open(sample(SAMPLE_XLSX)) ?: error("package not readable")
        val workbook = XlsxParser.parse(pkg)
        pkg.close()

        assertNotNull("the workbook should parse", workbook)
        assertTrue("the workbook should have sheets", workbook!!.sheets.isNotEmpty())
        val cells = workbook.sheets.first().rows.flatMap { it.cells }
        assertTrue("the workbook should have cells", cells.isNotEmpty())
        assertTrue(
            "a percent value should be formatted",
            cells.any { it.style.numberFormat?.contains("%") == true },
        )
    }

    @Test
    fun aPresentationIsParsed() {
        val pkg = OoxmlPackage.open(sample(SAMPLE_PPTX)) ?: error("package not readable")
        val deck = PptxParser.parse(pkg)
        pkg.close()

        assertNotNull("the deck should parse", deck)
        assertEquals("the deck should have three slides", 3, deck!!.slides.size)
        assertTrue(
            "the deck should have text boxes",
            deck.slides.first().shapes.any { it is PptxShape.TextBox },
        )
        assertTrue(
            "the deck should have a table",
            deck.slides.last().shapes.any { it is PptxShape.Table },
        )
    }

    companion object {
        private const val SAMPLE_DOCX = "2025全球技术架构演进方案及规范.docx"
        private const val SAMPLE_XLSX = "季度经营指标跟踪表.xlsx"
        private const val SAMPLE_PPTX = "产品发布会演讲稿.pptx"
    }
}
