package com.peng.pdfreader.ui.flow

/**
 * The start up flow of the app.
 *
 * splash -> onboarding -> permission -> home
 *
 * Every step is an activity of its own, the home activity does not know about
 * the steps that come before it.
 */
object AppFlow {

    /** The screen the app continues with. */
    enum class Step {
        /** The three page onboarding, shown on the very first start. */
        ONBOARDING,

        /** The request for the "access all files" permission. */
        PERMISSION,

        /** The home screen of the app. */
        HOME,
    }

    /**
     * Decides which screen comes next.
     *
     * @param onboardingShown whether the onboarding has already been seen
     * @param storageGranted whether the app may read the shared storage
     */
    fun nextStep(onboardingShown: Boolean, storageGranted: Boolean): Step = when {
        !onboardingShown -> Step.ONBOARDING
        !storageGranted -> Step.PERMISSION
        else -> Step.HOME
    }
}
