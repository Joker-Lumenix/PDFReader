package com.peng.pdfreader.feature.files.util

import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.utils.FileUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The helpers of the file list: the meta line and the type colour.
 */
class FileUiTest {

    private fun document(
        type: DocType = DocType.PDF,
        size: Long = 547L * 1024L,
        modified: Long = 1_700_000_000_000L,
    ) = Document(
        id = "id",
        name = "report.pdf",
        type = type,
        path = "/tmp/report.pdf",
        sizeBytes = size,
        createdAt = modified,
        modifiedAt = modified,
    )

    @Test
    fun `the meta line holds the date and the size`() {
        val meta = FileUi.meta(document())
        assertTrue("expected a separator in $meta", meta.contains(FileUi.SEPARATOR))
        assertTrue("expected a size in $meta", meta.contains("KB") || meta.contains("MB"))
    }

    @Test
    fun `sizes are shown in the unit that fits`() {
        assertTrue(FileUi.size(0).isNotEmpty())
        assertTrue(FileUi.size(1024).contains("KB"))
        assertTrue(FileUi.size(5L * 1024 * 1024).contains("MB"))
        assertTrue(FileUi.size(2L * 1024 * 1024 * 1024).contains("GB"))
    }

    @Test
    fun `the four types of the design get their own colour`() {
        val colours = listOf(DocType.PDF, DocType.WORD, DocType.EXCEL, DocType.PPT)
            .map { FileUi.typeColor(it) }
        assertEquals("every type needs its own colour", colours.size, colours.toSet().size)
    }

    @Test
    fun `the remaining types share the fallback colour`() {
        assertEquals(
            FileUi.typeColor(DocType.OTHER),
            FileUi.typeColor(DocType.IMAGE),
        )
        assertEquals(
            FileUi.typeColor(DocType.OTHER),
            FileUi.typeColor(DocType.TXT),
        )
    }
}
