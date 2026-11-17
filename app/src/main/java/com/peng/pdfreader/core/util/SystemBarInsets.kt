package com.peng.pdfreader.core.util

import android.os.Build
import android.view.WindowInsets

/**
 * The insets of the system bars.
 *
 * The app draws edge to edge, so every page has to know how much room the
 * status bar takes. The API of the insets changed with Android 11, this helper
 * hides the difference.
 */
object SystemBarInsets {

    /** The height of the status bar inset. */
    fun top(insets: WindowInsets): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.statusBars()).top
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetTop
        }

    /** The height of the navigation bar inset. */
    fun bottom(insets: WindowInsets): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }
}
