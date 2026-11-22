package com.peng.pdfreader.core.office

import com.peng.pdfreader.core.office.docx.DocxBlock
import com.peng.pdfreader.core.office.docx.DocxDocument
import com.peng.pdfreader.core.office.docx.DocxParser
import com.peng.pdfreader.core.office.edit.DocxEditor
import com.peng.pdfreader.core.office.edit.OoxmlArchive
import com.peng.pdfreader.core.office.edit.PptxEditor
import com.peng.pdfreader.core.office.edit.XlsxEditor
import com.peng.pdfreader.core.office.pptx.PptxParser
import com.peng.pdfreader.core.office.pptx.PptxShape
import com.peng.pdfreader.core.office.xlsx.XlsxParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The editors of the office documents against the real sample documents.
 *
 * The editors change the text of a document on the disk, so the number the
 * reader gives to a paragraph, to a cell or to a shape has to be the number
 * the editor finds in the file. The tests write a change into a copy of a
 * sample and read it back.
 */
class OfficeEditTest {

    private fun sample(name: String): File {
        val resource = javaClass.classLoader?.getResource("seed/$name")
            ?: error("the sample $name is missing")
        val target = File.createTempFile("sample-", ".tmp")
        target.deleteOnExit()
        File(resource.toURI()).copyTo(target, overwrite = true)
        return target
    }

    private fun copyOf(file: File): File {
        val target = File.createTempFile("edited-", ".tmp")
        target.deleteOnExit()
        file.copyTo(target, overwrite = true)
        return target
    }

    // ------------------------------------------------------------------- word

    /**
     * The text of a paragraph is written back into the document.
     */
    @Test
    fun theTextOfAParagraphIsWrittenBackIntoTheDocument() {
        val file = sample("big_document.docx")
        val before = paragraphsOf(file)
        val target = before.indexOfFirst { it.second.isNotBlank() }
        assertTrue("the document should have paragraphs with a text", target >= 0)
        val sourceIndex = before[target].first

        val archive = OoxmlArchive.open(file)
        assertNotNull("the package should be readable", archive)
        assertTrue(
            "the paragraph should be written",
            DocxEditor.setParagraphText(archive!!, sourceIndex, EDITED),
        )
        val out = copyOf(file)
        assertTrue("the package should be written", archive.writeTo(out))

        val after = paragraphsOf(out)
        assertEquals("the number of the paragraphs", before.size, after.size)
        assertEquals("the text of the paragraph", EDITED, after[target].second)
        // The other paragraphs did not change.
        before.forEachIndexed { index, entry ->
            if (index == target) return@forEachIndexed
            assertEquals("the text of the paragraph $index", entry.second, after[index].second)
        }
    }

    /**
     * The paragraphs of a document, in the order of the document.
     *
     * The paragraphs of the tables are part of the list as well, the editor
     * counts them the same way.
     */
    private fun paragraphsOf(file: File): List<Pair<Int, String>> {
        val pkg = OoxmlPackage.open(file) ?: error("the package should be readable")
        val document = try {
            DocxParser.parse(pkg)
        } finally {
            pkg.close()
        }
        assertNotNull("the document should parse", document)
        val result = ArrayList<Pair<Int, String>>()
        collect(document!!.blocks, result)
        result.sortBy { it.first }
        return result
    }

    private fun collect(blocks: List<DocxBlock>, result: MutableList<Pair<Int, String>>) {
        blocks.forEach { block ->
            when (block) {
                is DocxBlock.Paragraph -> result += block.sourceIndex to
                    block.runs.joinToString("") { it.text }

                is DocxBlock.Table -> block.rows.forEach { row ->
                    row.forEach { cell -> collect(cell.blocks, result) }
                }

                is DocxBlock.Image -> Unit
            }
        }
    }

    // ----------------------------------------------------------------- excel

    /**
     * The value of a cell is written back into the workbook.
     */
    @Test
    fun theValueOfACellIsWrittenBackIntoTheWorkbook() {
        val file = sample("sample_workbook.xlsx")
        val pkg = OoxmlPackage.open(file) ?: error("the package should be readable")
        val reader = XlsxParser.Reader(pkg)
        val part = reader.sheetPart(0)
        val sheet = reader.readSheet(0)
        pkg.close()
        assertNotNull("the workbook should have a sheet", sheet)
        assertNotNull("the sheet should have a part", part)
        val cell = sheet!!.rows.flatMap { it.cells }.firstOrNull { !it.isCovered && it.column == 1 }
        assertNotNull("the sheet should have a cell", cell)

        val archive = OoxmlArchive.open(file)
        assertNotNull("the package should be readable", archive)
        assertTrue(
            "the cell should be written",
            XlsxEditor.setCellValue(archive!!, part!!, cell!!.row, cell.column, EDITED),
        )
        val out = copyOf(file)
        assertTrue("the package should be written", archive.writeTo(out))

        val pkg2 = OoxmlPackage.open(out) ?: error("the package should be readable")
        val reader2 = XlsxParser.Reader(pkg2)
        val sheet2 = reader2.readSheet(0)
        pkg2.close()
        assertNotNull("the workbook should have a sheet", sheet2)
        val cell2 = sheet2!!.rows.flatMap { it.cells }
            .firstOrNull { it.row == cell.row && it.column == cell.column }
        assertNotNull("the cell should still be there", cell2)
        assertEquals("the value of the cell", EDITED, cell2!!.value)
    }

    /**
     * The reference of a cell is the way excel writes it.
     */
    @Test
    fun theReferenceOfACellIsWrittenTheWayExcelWritesIt() {
        assertEquals("A1", XlsxEditor.referenceOf(0, 1))
        assertEquals("B3", XlsxEditor.referenceOf(1, 3))
        assertEquals("Z9", XlsxEditor.referenceOf(25, 9))
        assertEquals("AA10", XlsxEditor.referenceOf(26, 10))
    }

    // ------------------------------------------------------------------ power

    /**
     * The text of a text box is written back into the slide.
     */
    @Test
    fun theTextOfATextBoxIsWrittenBackIntoTheSlide() {
        val file = sample("big_presentation.pptx")
        val pkg = OoxmlPackage.open(file) ?: error("the package should be readable")
        val reader = PptxParser.Reader(pkg)
        val part = reader.slidePart(0)
        val slide = reader.readSlide(0)
        pkg.close()
        assertNotNull("the deck should have a slide", slide)
        assertNotNull("the slide should have a part", part)
        val shape = slide!!.shapes.filterIsInstance<PptxShape.TextBox>()
            .firstOrNull { it.paragraphs.isNotEmpty() && it.sourceIndex >= 0 }
        assertNotNull("the slide should have a text box", shape)

        val archive = OoxmlArchive.open(file)
        assertNotNull("the package should be readable", archive)
        assertTrue(
            "the text box should be written",
            PptxEditor.setShapeText(archive!!, part!!, shape!!.sourceIndex, EDITED),
        )
        val out = copyOf(file)
        assertTrue("the package should be written", archive.writeTo(out))

        val pkg2 = OoxmlPackage.open(out) ?: error("the package should be readable")
        val slide2 = PptxParser.Reader(pkg2).readSlide(0)
        pkg2.close()
        assertNotNull("the deck should have a slide", slide2)
        val shape2 = slide2!!.shapes.filterIsInstance<PptxShape.TextBox>()
            .firstOrNull { it.sourceIndex == shape.sourceIndex }
        assertNotNull("the text box should still be there", shape2)
        assertEquals("the text of the text box", EDITED, shape2!!.paragraphs.joinToString("") { it.text })
    }

    private companion object {
        /** The text the tests write into the documents. */
        const val EDITED = "edited by the test"
    }
}
