package com.peng.pdfreader.core.office

import com.peng.pdfreader.core.office.docx.DocxBlock
import com.peng.pdfreader.core.office.docx.DocxParser
import com.peng.pdfreader.core.office.pptx.PptxParser
import com.peng.pdfreader.core.office.pptx.PptxShape
import com.peng.pdfreader.core.office.xlsx.XlsxParser
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

    /**
     * The shapes of a slide live in the coordinate space of the slide.
     *
     * The reader draws them with the origin of the slide, so the parser has to
     * deliver the coordinates of the slide and not the ones of the page.
     */
    @Test
    fun theShapesOfASlideUseTheCoordinatesOfTheSlide() {
        val pkg = OoxmlPackage.open(sample(SAMPLE_PPTX)) ?: error("package not readable")
        val deck = PptxParser.parse(pkg)
        pkg.close()

        val slide = deck!!.slides.first()
        val boxes = slide.shapes.filter { it.widthDp > 0f && it.heightDp > 0f }
        assertTrue("the slide should have shapes with a geometry", boxes.isNotEmpty())
        boxes.forEach { shape ->
            assertTrue(
                "a shape of the slide should be inside of it, but $shape is not",
                shape.leftDp >= 0f && shape.topDp >= 0f &&
                    shape.leftDp + shape.widthDp <= deck.widthDp + 1f &&
                    shape.topDp + shape.heightDp <= deck.heightDp + 1f,
            )
        }
        // The first text box of the sample sits at 113.4dp / 234.3dp.
        val first = boxes.minByOrNull { it.topDp }
        assertNotNull("the slide should have a shape", first)
        assertEquals("the left edge of the shape", 113.4f, first!!.leftDp, 0.5f)
        assertEquals("the top edge of the shape", 234.3f, first.topDp, 0.5f)
    }

    companion object {
        private const val SAMPLE_DOCX = "2025全球技术架构演进方案及规范.docx"
        private const val SAMPLE_XLSX = "季度经营指标跟踪表.xlsx"
        private const val SAMPLE_PPTX = "产品发布会演讲稿.pptx"
    }
}
