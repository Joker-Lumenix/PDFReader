package com.peng.pdfreader.ui.flow

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The start up flow decides which screen the app continues with, so the rules
 * are pinned down here.
 */
class AppFlowTest {

    @Test
    fun `the guide comes first`() {
        assertEquals(
            AppFlow.Step.GUIDE,
            AppFlow.nextStep(guideShown = false, storageGranted = false),
        )
        assertEquals(
            AppFlow.Step.GUIDE,
            AppFlow.nextStep(guideShown = false, storageGranted = true),
        )
    }

    @Test
    fun `the permission comes after the guide`() {
        assertEquals(
            AppFlow.Step.PERMISSION,
            AppFlow.nextStep(guideShown = true, storageGranted = false),
        )
    }

    @Test
    fun `the home comes last`() {
        assertEquals(
            AppFlow.Step.HOME,
            AppFlow.nextStep(guideShown = true, storageGranted = true),
        )
    }
}
