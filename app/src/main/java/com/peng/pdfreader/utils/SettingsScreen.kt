package com.peng.pdfreader.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.util.Logger

/**
 * Starts the screens of this feature.
 *
 * The screens are plain activities that the shell registers in the manifest,
 * so a missing registration must not crash the app - it is logged and the
 * user gets a short hint instead.
 */
object SettingsScreen {

    private const val TAG = "SettingsScreen"

    fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Logger.e(TAG, "screen is not registered in the manifest", error)
            Toast.makeText(context, R.string.settings_screen_missing, Toast.LENGTH_SHORT).show()
        }
    }
}
