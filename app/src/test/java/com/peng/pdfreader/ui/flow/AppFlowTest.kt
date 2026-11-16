package com.peng.pdfreader.ui.flow

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The start up flow decides which screen the app continues with, so the rules
 * are pinned down here.
 */
class AppFlowTest {

    @Test
    fun `the onboarding comes first`() {
        assertEquals(
            AppFlow.Step.ONBOARDING,
            AppFlow.nextStep(onboardingShown = false, storageGranted = false),
        )
        assertEquals(
            AppFlow.Step.ONBOARDING,
            AppFlow.nextStep(onboardingShown = false, storageGranted = true),
        )
    }

    @Test
    fun `the permission comes after the onboarding`() {
        assertEquals(
            AppFlow.Step.PERMISSION,
            AppFlow.nextStep(onboardingShown = true, storageGranted = false),
        )
    }

    @Test
    fun `the home comes last`() {
        assertEquals(
            AppFlow.Step.HOME,
            AppFlow.nextStep(onboardingShown = true, storageGranted = true),
        )
    }
}
