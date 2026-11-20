package com.peng.pdfreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The formats the app supports.
 *
 * The app is a document reader: the PDF, the Word, the Excel and the
 * PowerPoint files are read, every other format is refused.
 */
class DocTypeTest {

    @Test
    fun `the four document formats are detected by their name`() {
        assertEquals(DocType.PDF, DocType.of("report.pdf"))
        assertEquals(DocType.WORD, DocType.of("memo.doc"))
        assertEquals(DocType.WORD, DocType.of("memo.docx"))
        assertEquals(DocType.EXCEL, DocType.of("budget.xls"))
        assertEquals(DocType.EXCEL, DocType.of("budget.xlsx"))
        assertEquals(DocType.PPT, DocType.of("talk.ppt"))
        assertEquals(DocType.PPT, DocType.of("talk.pptx"))
    }

    @Test
    fun `the name of the file is read without its case`() {
        assertEquals(DocType.PDF, DocType.of("REPORT.PDF"))
        assertEquals(DocType.EXCEL, DocType.of("Budget.Xlsx"))
    }

    @Test
    fun `the other formats of the device are not supported`() {
        val unsupported = listOf(
            "notes.txt",
            "readme.md",
            "chart.png",
            "photo.jpg",
            "scan.jpeg",
            "picture.webp",
            "archive.zip",
            "setup.bin",
            "report",
            "",
        )
        unsupported.forEach { name ->
            assertNull("$name must not be supported", DocType.of(name))
        }
    }

    @Test
    fun `the mime types of the four formats are detected`() {
        assertEquals(DocType.PDF, DocType.ofMime("application/pdf"))
        assertEquals(DocType.WORD, DocType.ofMime("application/msword"))
        assertEquals(
            DocType.WORD,
            DocType.ofMime("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        )
        assertEquals(DocType.EXCEL, DocType.ofMime("application/vnd.ms-excel"))
        assertEquals(
            DocType.EXCEL,
            DocType.ofMime("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        )
        assertEquals(DocType.PPT, DocType.ofMime("application/vnd.ms-powerpoint"))
        assertEquals(
            DocType.PPT,
            DocType.ofMime("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
        )
    }

    @Test
    fun `the mime types of the other formats are not supported`() {
        val unsupported = listOf(
            null,
            "",
            "text/plain",
            "image/png",
            "application/zip",
            "application/octet-stream",
            "application/vnd.oasis.opendocument.text",
        )
        unsupported.forEach { mime ->
            assertNull("$mime must not be supported", DocType.ofMime(mime))
        }
    }

    @Test
    fun `the mime type of a file follows its extension`() {
        assertEquals("application/pdf", DocType.mimeTypeOf("report.pdf"))
        assertEquals("application/msword", DocType.mimeTypeOf("memo.doc"))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            DocType.mimeTypeOf("memo.docx"),
        )
        assertEquals("application/vnd.ms-excel", DocType.mimeTypeOf("budget.xls"))
        assertEquals("application/vnd.ms-powerpoint", DocType.mimeTypeOf("talk.ppt"))
        assertNull(DocType.mimeTypeOf("notes.txt"))
    }
}
