package com.peng.pdfreader.permission

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The schedule of the prompt of the default reader.
 *
 * The prompt shows up after the first effective read, then it waits two days
 * or two more effective reads, and it stays away after the third refusal.
 */
class DefaultAppPromptTest {

    /** The current time of the test. */
    private val now = 1_700_000_000_000L

    /** Two days in milliseconds. */
    private val twoDays = 2L * 24L * 60L * 60L * 1000L

    @Test
    fun `the first effective read shows the prompt`() {
        assertTrue(
            DefaultAppPrompt.isDue(
                effectiveReads = 1,
                readsAtLastPrompt = 0,
                shownAt = 0L,
                now = now,
            ),
        )
    }

    @Test
    fun `the prompt waits after a refusal`() {
        assertFalse(
            "the prompt waits for the cooldown",
            DefaultAppPrompt.isDue(
                effectiveReads = 1,
                readsAtLastPrompt = 1,
                shownAt = now,
                now = now + 60_000L,
            ),
        )
    }

    @Test
    fun `the prompt comes back after two days`() {
        assertTrue(
            DefaultAppPrompt.isDue(
                effectiveReads = 1,
                readsAtLastPrompt = 1,
                shownAt = now,
                now = now + twoDays,
            ),
        )
    }

    @Test
    fun `the prompt comes back after two more effective reads`() {
        assertTrue(
            DefaultAppPrompt.isDue(
                effectiveReads = 3,
                readsAtLastPrompt = 1,
                shownAt = now,
                now = now + 60_000L,
            ),
        )
    }

    @Test
    fun `one more effective read is not enough`() {
        assertFalse(
            DefaultAppPrompt.isDue(
                effectiveReads = 2,
                readsAtLastPrompt = 1,
                shownAt = now,
                now = now + 60_000L,
            ),
        )
    }
}
