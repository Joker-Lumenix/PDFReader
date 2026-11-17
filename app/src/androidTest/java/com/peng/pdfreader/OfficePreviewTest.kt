package com.peng.pdfreader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.peng.pdfreader.core.office.OoxmlPackage
import com.peng.pdfreader.core.office.docx.DocxBlock
import com.peng.pdfreader.core.office.docx.DocxParser
import com.peng.pdfreader.core.office.pptx.PptxParser
import com.peng.pdfreader.core.office.pptx.PptxShape
import com.peng.pdfreader.core.office.xlsx.XlsxParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Verifies the offline Office parsers against the genuine sample files in the APK assets. */
@RunWith(AndroidJUnit4::class)
class OfficePreviewTest {

    private fun assetFile(namePart: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = requireNotNull(context.assets.list("seed")) { "seed assets missing" }
            .first { it.contains(namePart) }
        val target = File(context.cacheDir, "preview-test/$name")
        target.parentFile?.mkdirs()
        context.assets.open("seed/$name").use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun wordFile(namePart: String): File = assetFile(namePart)

    private fun excelFile(namePart: String): File = assetFile(namePart)

    private fun powerPointFile(namePart: String): File = assetFile(namePart)

    @Test
    fun docx_parsesHeadingsParagraphsTableAndImage() {
        val pkg = OoxmlPackage.open(wordFile("架构")) ?: error("package not readable")
        val document = DocxParser.parse(pkg) ?: error("docx parse failed")
        pkg.close()

        assertTrue("title should carry the document name", document.title.contains("架构"))
        assertTrue("A4 page expected", document.pageWidthPt in 500f..620f)
        assertTrue("page margins expected", document.marginLeftPt > 20f)
        assertTrue("expected many blocks, got ${document.blocks.size}", document.blocks.size >= 10)

        val paragraphs = document.blocks.filterIsInstance<DocxBlock.Paragraph>()
        assertTrue("expected headings", paragraphs.any { it.headingLevel == 1 })
        assertTrue("expected bullet list items", paragraphs.any { it.listLabel != null })
        assertTrue("expected bold runs", paragraphs.any { p -> p.runs.any { it.bold } })
        assertTrue("expected centred paragraph", paragraphs.any { it.alignment == "center" })

        val table = document.blocks.filterIsInstance<DocxBlock.Table>().firstOrNull()
        assertNotNull("expected a table", table)
        assertEquals(4, table!!.rows.size)
        assertEquals(3, table.rows.first().size)
        assertTrue("expected a shaded header cell", table.rows.first().first().background != null)

        val image = document.blocks.filterIsInstance<DocxBlock.Image>().firstOrNull()
        assertNotNull("expected an inline image", image)
        assertTrue("image payload expected", image!!.bytes.size > 1000)
    }

    @Test
    fun xlsx_parsesSheetsValuesNumbersAndStyles() {
        val pkg = OoxmlPackage.open(excelFile("经营指标")) ?: error("package not readable")
        val workbook = XlsxParser.parse(pkg) ?: error("xlsx parse failed")
        pkg.close()

        assertEquals(2, workbook.sheets.size)
        val sheet = workbook.sheets.first()
        assertEquals("经营指标", sheet.name)
        assertTrue("expected frozen header", sheet.frozenRows >= 1)
        assertTrue("expected column widths", sheet.columnWidthsDp.size >= 6)

        val cells = sheet.rows.flatMap { it.cells }
        val header = cells.firstOrNull { it.row == 3 && it.column == 0 }
        assertEquals("季度", header?.value)
        assertTrue("expected a bold header", header?.style?.bold == true)
        assertTrue("expected a header fill", header?.style?.background != null)

        val ratio = cells.firstOrNull { it.row == 7 && it.column == 3 }
        assertNotNull("expected the completion rate cell", ratio)
        assertTrue("percent format expected: ${ratio!!.style.numberFormat}", ratio.style.numberFormat?.contains("%") == true)
        assertTrue("expected a percent value: ${ratio.value}", ratio.value.contains("%"))

        val revenue = cells.firstOrNull { it.row == 7 && it.column == 2 }
        assertTrue("thousand separated value expected: ${revenue?.value}", revenue?.value?.contains(",") == true)

        val total = cells.firstOrNull { it.row == 8 && it.column == 2 }
        assertNotNull("expected a formula cell", total)
        assertTrue("expected a formula", total!!.formula != null)

        val merged = workbook.sheets.first().rows.firstOrNull { it.index == 1 }
            ?.cells?.firstOrNull { it.column == 0 }
        assertTrue("expected a merged title cell", merged?.columnSpan ?: 0 > 1)
    }

    @Test
    fun pptx_parsesSlidesShapesPicturesAndTables() {
        val pkg = OoxmlPackage.open(powerPointFile("演讲稿")) ?: error("package not readable")
        val deck = PptxParser.parse(pkg) ?: error("pptx parse failed")
        pkg.close()

        assertEquals(3, deck.slides.size)
        assertTrue("16:9 slide expected", deck.widthDp / deck.heightDp > 1.5f)

        val first = deck.slides.first()
        assertTrue("expected text boxes", first.shapes.any { it is PptxShape.TextBox })
        val text = first.shapes.filterIsInstance<PptxShape.TextBox>().first()
        assertTrue("expected a title", text.paragraphs.any { it.text.contains("端侧文档引擎") })
        assertTrue("expected a centred title", text.paragraphs.any { it.style.alignment == "center" })

        val second = deck.slides[1]
        assertTrue("expected a picture", second.shapes.any { it is PptxShape.Picture })
        val bullets = second.shapes.filterIsInstance<PptxShape.TextBox>()
            .flatMap { it.paragraphs }
            .filter { it.bullet != null }
        assertTrue("expected bulleted paragraphs", bullets.isNotEmpty())

        val third = deck.slides[2]
        assertTrue("expected a table", third.shapes.any { it is PptxShape.Table })
        val table = third.shapes.filterIsInstance<PptxShape.Table>().first()
        assertEquals(4, table.rows.size)
        assertEquals(3, table.rows.first().size)
        assertTrue("expected a header cell", table.rows.first().first().text.isNotEmpty())
    }
}
