package com.peng.pdfreader.feature.onboarding

import android.content.Context
import com.peng.pdfreader.data.prefs.AppPrefs

/**
 * Remembers that the onboarding has been shown.
 *
 * The flag lives in the app preference store together with the other settings.
 */
internal object OnboardingPrefs {

    fun isShown(context: Context): Boolean = AppPrefs.get(context).onboardingShown

    fun setShown(context: Context, shown: Boolean) {
        AppPrefs.get(context).onboardingShown = shown
    }
}
