package com.peng.pdfreader.feature.onboarding

import android.content.Intent
import androidx.fragment.app.FragmentActivity
import com.peng.pdfreader.appcore.util.Logger

/**
 * Starts the onboarding, but only the first time the app is used.
 *
 * The shell calls this once after the first tab is shown; the flag keeps the
 * onboarding from coming back on every start.
 */
object OnboardingLauncher {

    private const val TAG = "OnboardingLauncher"

    /**
     * Starts the onboarding when it has not been shown yet.
     *
     * @param launch starts the intent, the shell uses its own launcher so it
     * can react to the end of the onboarding.
     * @return true when the onboarding was started.
     */
    fun showIfNeeded(activity: FragmentActivity, launch: (Intent) -> Unit): Boolean {
        if (OnboardingPrefs.isShown(activity)) return false
        OnboardingPrefs.setShown(activity, true)
        Logger.i(TAG, "showing the onboarding")
        launch(Intent(activity, OnboardingActivity::class.java))
        return true
    }
}
