package com.peng.pdfreader.core.office.docx

import com.peng.pdfreader.core.office.OoxmlPackage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The merged cells of a Word document.
 *
 * A cell that is merged with the cells below it stands over all of them - it
 * is the way the office software draws it.
 */
class DocxMergeTest {

    /** A document with a table whose first column is merged over two rows. */
    private fun documentWithMergedCells(): File {
        val file = File.createTempFile("merged-", ".docx")
        file.deleteOnExit()
        val body = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:tbl>
                  <w:tblGrid><w:gridCol w:w="2000"/><w:gridCol w:w="3000"/></w:tblGrid>
                  <w:tr>
                    <w:tc>
                      <w:tcPr><w:vMerge w:val="restart"/></w:tcPr>
                      <w:p><w:r><w:t>the merged cell</w:t></w:r></w:p>
                    </w:tc>
                    <w:tc><w:p><w:r><w:t>the first row</w:t></w:r></w:p></w:tc>
                  </w:tr>
                  <w:tr>
                    <w:tc>
                      <w:tcPr><w:vMerge/></w:tcPr>
                      <w:p/>
                    </w:tc>
                    <w:tc><w:p><w:r><w:t>the second row</w:t></w:r></w:p></w:tc>
                  </w:tr>
                </w:tbl>
                <w:p><w:r><w:t>after the table</w:t></w:r></w:p>
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

    /**
     * The cells that are merged are found.
     */
    @Test
    fun theCellsThatAreMergedAreFound() {
        val file = documentWithMergedCells()
        val pkg = OoxmlPackage.open(file) ?: error("the package should be readable")
        val document = DocxParser.parse(pkg)
        pkg.close()
        assertTrue("the document should parse", document != null)
        val table = document!!.blocks.filterIsInstance<DocxBlock.Table>().firstOrNull()
        assertTrue("the document should have a table", table != null)
        val rows = table!!.rows
        assertEquals("the number of the rows", 2, rows.size)

        // The first cell of the first row stands over the two rows.
        assertEquals("the cell of the first row", 2, rows[0][0].rowSpan)
        assertEquals("the merge of the first row", "restart", rows[0][0].vMerge)
        // The cell of the second row belongs to the cell above it.
        assertEquals("the cell of the second row", 0, rows[1][0].rowSpan)
        assertEquals("the merge of the second row", "continue", rows[1][0].vMerge)
        // The other cells are not merged.
        assertEquals("the cell beside it", 1, rows[0][1].rowSpan)
        assertEquals("the cell of the second row", 1, rows[1][1].rowSpan)
    }
}
