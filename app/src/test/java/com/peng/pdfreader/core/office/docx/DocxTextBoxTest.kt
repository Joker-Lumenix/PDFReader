package com.peng.pdfreader.core.office.docx

import com.peng.pdfreader.core.office.OoxmlPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The reader of the text boxes of a Word document.
 *
 * A text box holds a text of its own. The office software shows it above the
 * text of the document, the reader shows it as a block of its own so the
 * content of the document is complete.
 */
class DocxTextBoxTest {

    /** A document with a text box and a paragraph. */
    private fun documentWithTextBox(): File {
        val file = File.createTempFile("textbox-", ".docx")
        file.deleteOnExit()
        val body = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
                xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
                xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape">
              <w:body>
                <w:p><w:r><w:t>the text of the document</w:t></w:r></w:p>
                <w:p>
                  <w:r>
                    <w:drawing>
                      <wp:inline>
                        <a:graphic>
                          <a:graphicData>
                            <wps:wsp>
                              <wps:txbx>
                                <w:txbxContent>
                                  <w:p><w:r><w:t>the text of the box</w:t></w:r></w:p>
                                </w:txbxContent>
                              </wps:txbx>
                            </wps:wsp>
                          </a:graphicData>
                        </a:graphic>
                      </wp:inline>
                    </w:drawing>
                  </w:r>
                </w:p>
                <w:p><w:r><w:t>the last paragraph</w:t></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent()
        val contentTypes = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="xml" ContentType="application/xml"/>
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>
        """.trimIndent()
        val rels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
        """.trimIndent()
        ZipOutputStream(file.outputStream().buffered()).use { out ->
            out.putNextEntry(ZipEntry("[Content_Types].xml"))
            out.write(contentTypes.toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("_rels/.rels"))
            out.write(rels.toByteArray())
            out.closeEntry()
            out.putNextEntry(ZipEntry("word/document.xml"))
            out.write(body.toByteArray())
            out.closeEntry()
        }
        return file
    }

    /** The text of the document, every paragraph of it. */
    private fun textOf(document: DocxDocument): String =
        document.blocks
            .filterIsInstance<DocxBlock.Paragraph>()
            .joinToString("\n") { it.runs.joinToString("") { run -> run.text } }

    /**
     * The text of a text box is shown.
     */
    @Test
    fun theTextOfATextBoxIsShown() {
        val file = documentWithTextBox()
        val pkg = OoxmlPackage.open(file) ?: error("the package should be readable")
        val document = DocxParser.parse(pkg)
        pkg.close()
        assertTrue("the document should parse", document != null)
        val text = textOf(document!!)
        assertTrue(
            "the text of the document should be there, was $text",
            text.contains("the text of the document"),
        )
        assertTrue(
            "the text of the box should be there, was $text",
            text.contains("the text of the box"),
        )
        assertTrue(
            "the text after the box should be there, was $text",
            text.contains("the last paragraph"),
        )
    }

    /**
     * The paragraphs of the document keep their number.
     *
     * The editor counts the paragraphs of the file, the text boxes are not
     * part of the flow of the document, so they do not change the number.
     */
    @Test
    fun theTextBoxesDoNotChangeTheNumberOfTheParagraphs() {
        val file = documentWithTextBox()
        val pkg = OoxmlPackage.open(file) ?: error("the package should be readable")
        val document = DocxParser.parse(pkg)
        pkg.close()
        val paragraphs = document!!.blocks.filterIsInstance<DocxBlock.Paragraph>()
        val numbers = paragraphs.map { it.sourceIndex }
        // The paragraph of the text box has no number, the editor does not
        // write into it.
        assertEquals("the number of the paragraphs", listOf(0, 1, -1, 2), numbers)
        assertEquals(
            "the text of the box",
            "the text of the box",
            paragraphs[2].runs.joinToString("") { it.text },
        )
    }
}
