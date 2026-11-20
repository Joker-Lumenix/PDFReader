package com.peng.pdfreader.permission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The counter of the effective reads.
 *
 * A read is effective when the user stays with the document for half a minute
 * or when they turn three pages.
 */
class EffectiveReadTrackerTest {

    /** The clock of the test. */
    private var time = 0L

    /** How often the tracker reported an effective read. */
    private var reads = 0

    private fun tracker() = EffectiveReadTracker(onEffectiveRead = { reads++ }, now = { time })

    @Test
    fun `three pages are an effective read`() {
        val tracker = tracker()
        tracker.onOpened(0)
        tracker.onPage(1)
        tracker.onPage(2)
        assertEquals("two pages are not enough", 0, reads)
        tracker.onPage(3)
        assertEquals("the third page counts", 1, reads)
    }

    @Test
    fun `half a minute of reading is an effective read`() {
        val tracker = tracker()
        tracker.onOpened(0)
        time = 29_000L
        tracker.check()
        assertEquals("29 seconds are not enough", 0, reads)
        time = 30_000L
        tracker.check()
        assertEquals("the time of the reading counts", 1, reads)
    }

    @Test
    fun `the check counts the time while the document is open`() {
        val tracker = tracker()
        tracker.onOpened(0)
        time = 45_000L
        tracker.check()
        assertEquals("the user reads the document", 1, reads)
    }

    @Test
    fun `a short look is not an effective read`() {
        val tracker = tracker()
        tracker.onOpened(0)
        time = 5_000L
        tracker.onPage(1)
        tracker.onClosed()
        assertEquals("a short look does not count", 0, reads)
    }

    @Test
    fun `the time in the background does not count`() {
        val tracker = tracker()
        tracker.onOpened(0)
        tracker.onPaused()
        time = 120_000L
        tracker.check()
        assertEquals("the app was in the background", 0, reads)
        tracker.onResumed()
        time = 121_000L
        tracker.check()
        assertEquals("only the second stretch counts", 0, reads)
        time = 151_000L
        tracker.check()
        assertEquals("half a minute in the foreground counts", 1, reads)
    }

    @Test
    fun `a read is only counted once`() {
        val tracker = tracker()
        tracker.onOpened(0)
        tracker.onPage(1)
        tracker.onPage(2)
        tracker.onPage(3)
        tracker.onPage(4)
        tracker.onClosed()
        assertEquals("the read of a document counts once", 1, reads)
    }

    @Test
    fun `the pages of the reader are not counted twice`() {
        val tracker = tracker()
        tracker.onOpened(0)
        tracker.onPage(0)
        tracker.onPage(0)
        tracker.onPage(0)
        assertEquals("the same page is no turn", 0, reads)
    }

    @Test
    fun `a document that is not open does not count`() {
        val tracker = tracker()
        tracker.onPage(1)
        tracker.onPage(2)
        tracker.onPage(3)
        assertFalse("there is no document", reads > 0)
        assertEquals(0, reads)
    }

    @Test
    fun `the check of a closed document does nothing`() {
        val tracker = tracker()
        tracker.onOpened(0)
        tracker.onClosed()
        time = 60_000L
        tracker.check()
        assertEquals("the document is closed", 0, reads)
    }
}
