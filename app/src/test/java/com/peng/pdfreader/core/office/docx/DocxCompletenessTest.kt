package com.peng.pdfreader.core.office.docx

import com.peng.pdfreader.core.office.OoxmlPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The reader of a Word document against a real sample.
 *
 * The test compares the text of the document with the text that is written in
 * the file: a document that loses a part of its content shows it here.
 */
class DocxCompletenessTest {

    private fun sample(name: String): File {
        val resource = javaClass.classLoader?.getResource("seed/$name")
            ?: error("the sample $name is missing")
        val target = File.createTempFile("sample-", ".tmp")
        target.deleteOnExit()
        File(resource.toURI()).copyTo(target, overwrite = true)
        return target
    }

    /** The text of the document, every paragraph of it. */
    private fun textOf(document: DocxDocument): String {
        val builder = StringBuilder()
        fun walk(blocks: List<DocxBlock>) {
            blocks.forEach { block ->
                when (block) {
                    is DocxBlock.Paragraph -> builder.append(
                        block.runs.joinToString("") { it.text },
                    ).append('\n')

                    is DocxBlock.Table -> block.rows.forEach { row ->
                        row.forEach { cell -> walk(cell.blocks) }
                    }

                    is DocxBlock.Image -> Unit
                }
            }
        }
        walk(document.blocks)
        return builder.toString()
    }

    /** The text of the file, every piece of text of the document. */
    private fun textOfFile(file: File): String {
        val pkg = OoxmlPackage.open(file) ?: error("the package should be readable")
        val node = pkg.node("word/document.xml")
        pkg.close()
        val builder = StringBuilder()
        fun walk(current: com.peng.pdfreader.core.office.OoxmlNode) {
            if (current.localName == "t") {
                builder.append(current.text)
                return
            }
            current.nodes()?.forEach { walk(it) }
        }
        node?.let { walk(it) }
        return builder.toString()
    }

    /**
     * The document shows all of its text.
     */
    @Test
    fun theDocumentShowsAllOfItsText() {
        assertComplete("big_document.docx")
    }

    /**
     * The samples of the app are complete as well.
     */
    @Test
    fun theSamplesOfTheAppShowAllOfTheirText() {
        assertComplete("2025全球技术架构演进方案及规范.docx")
        assertComplete("财务部2025Q2经营分析报告.docx")
    }

    /** The reader shows every piece of text of the document. */
    private fun assertComplete(name: String) {
        val file = sample(name)
        val pkg = OoxmlPackage.open(file) ?: error("the package should be readable")
        val document = DocxParser.parse(pkg)
        pkg.close()
        assertTrue("the document $name should parse", document != null)
        val parsed = textOf(document!!)
        val raw = textOfFile(file)
        assertTrue("the document $name should have text", raw.isNotBlank())
        // The text of the file is the text of the document, the reader only
        // adds the line breaks of the paragraphs.
        val expected = raw.filter { it != '\n' && it != '\r' && it != '\t' }
        val actual = parsed.filter { it != '\n' && it != '\r' && it != '\t' }
        assertEquals("the text of the document $name", expected, actual)
    }
}
