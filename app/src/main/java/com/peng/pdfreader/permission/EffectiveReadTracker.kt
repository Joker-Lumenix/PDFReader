package com.peng.pdfreader.permission

import android.os.SystemClock

/**
 * Counts the effective reading of a document.
 *
 * A read is effective when the user stays with the document for half a minute
 * or when they turn three pages. The time the app spends in the background
 * does not count.
 */
class EffectiveReadTracker(
    private val onEffectiveRead: () -> Unit,
    /** The clock of the tracker, the uptime of the device by default. */
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
) {

    /** Whether a document is open. */
    private var open = false

    /** Whether the app is in the foreground. */
    private var inForeground = false

    /** When the app came to the foreground. */
    private var foregroundSince = 0L

    /** The time the user spent with the document in the foreground. */
    private var readMs = 0L

    /** The pages the user turned. */
    private var pagesTurned = 0

    /** The page the reader showed the last time. */
    private var lastPage = -1

    /** Whether the read of this document was already counted. */
    private var counted = false

    /** The document is open, the reading starts. */
    fun onOpened(page: Int) {
        open = true
        inForeground = true
        foregroundSince = now()
        readMs = 0L
        pagesTurned = 0
        lastPage = page
        counted = false
    }

    /** The app comes to the foreground. */
    fun onResumed() {
        if (counted || !open) return
        inForeground = true
        foregroundSince = now()
    }

    /** The app goes to the background, the reading stops. */
    fun onPaused() {
        if (!inForeground) return
        readMs += now() - foregroundSince
        inForeground = false
    }

    /** The reader shows another page. */
    fun onPage(page: Int) {
        if (counted || !open) return
        if (lastPage >= 0 && page != lastPage) pagesTurned++
        lastPage = page
        check()
    }

    /** The document is closed. */
    fun onClosed() {
        if (!open) return
        onPaused()
        check()
        open = false
        inForeground = false
    }

    /**
     * Whether the reading of the document was effective.
     *
     * The reader calls it every few seconds while the user reads, so the
     * prompt of the default reader shows up while the document is open - not
     * only when it is closed.
     */
    fun check() {
        if (counted || !open) return
        if (readTime() < MIN_READ_MS && pagesTurned < MIN_PAGES) return
        counted = true
        onEffectiveRead()
    }

    /**
     * The time the user spent with the document so far.
     *
     * The time of the current foreground stretch is added on the fly, the
     * document may still be open.
     */
    private fun readTime(): Long {
        val current = if (inForeground) now() - foregroundSince else 0L
        return readMs + current
    }

    companion object {
        /** The time the user has to spend with the document. */
        private const val MIN_READ_MS = 30_000L

        /** The pages the user has to turn. */
        private const val MIN_PAGES = 3

        /** The time between two checks of the reading. */
        const val CHECK_INTERVAL_MS = 5_000L
    }
}
