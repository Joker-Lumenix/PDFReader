package com.peng.pdfreader.core.office

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The parsers of the core package read a document into a tree of XML nodes.
 *
 * A big document does not fit into the memory of a phone that way, so the text
 * is read with a streaming parser. These tests use the big documents of the
 * test resources.
 */
class OfficeTextTest {

    @Test
    fun theTextOfABigWordDocumentIsRead() {
        val lines = lines("seed/big_document.docx")
        assertTrue("the big Word document has text", lines.isNotEmpty())
        assertTrue("the first paragraph is read", lines.first().contains("段落"))
    }

    @Test
    fun theTextOfABigPresentationIsRead() {
        val lines = lines("seed/big_presentation.pptx")
        assertTrue("the big presentation has text", lines.isNotEmpty())
        assertTrue("the first slide is read", lines.any { it.contains("页标题") })
    }

    private fun lines(path: String): List<String> {
        val file = file(path) ?: error("the test document $path is missing")
        return OfficeText.lines(file)
    }

    private fun file(path: String): File? =
        javaClass.classLoader?.getResource(path)?.let { File(it.toURI()) }
}
