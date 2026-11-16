package com.peng.pdfreader.appcore.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The page range syntax of the split tool: `1-3,5,8-`.
 */
class PdfRangeTest {

    @Test
    fun `a single page becomes a range of one`() {
        assertEquals(listOf(1..1), PdfProcessor.parseRangeExpression("1"))
    }

    @Test
    fun `a range is expanded`() {
        assertEquals(listOf(1..3), PdfProcessor.parseRangeExpression("1-3"))
    }

    @Test
    fun `several parts are read in order`() {
        assertEquals(listOf(1..3, 5..5, 8..10), PdfProcessor.parseRangeExpression("1-3,5,8-10"))
    }

    @Test
    fun `an open end runs to the last page`() {
        assertEquals(listOf(8..10), PdfProcessor.parseRangeExpression("8-").let {
            PdfProcessor.parsePageNumbers("8-", 10).let { pages ->
                listOf(pages.min()..pages.max())
            }
        })
    }

    @Test
    fun `spaces are ignored`() {
        assertEquals(listOf(1..2, 4..4), PdfProcessor.parseRangeExpression(" 1 - 2 , 4 "))
    }

    @Test
    fun `page numbers are clipped to the document`() {
        assertEquals(listOf(1, 2, 3), PdfProcessor.parsePageNumbers("1-99", 3))
    }

    @Test
    fun `duplicates are removed and the order is kept`() {
        assertEquals(listOf(1, 2, 3), PdfProcessor.parsePageNumbers("3,1,2,1", 5))
    }

    @Test
    fun `an empty expression selects nothing`() {
        assertEquals(emptyList<Int>(), PdfProcessor.parsePageNumbers("", 10))
        assertEquals(emptyList<Int>(), PdfProcessor.parsePageNumbers("   ", 10))
    }
}
