package com.peng.pdfreader.core.util

import android.util.Log
import com.peng.pdfreader.BuildConfig

/**
 * Unified logging entry point.
 *
 * Debug builds may log freely, including Chinese messages. Release builds are
 * completely silent: the guard below short circuits every call and the
 * release ProGuard rules additionally strip any direct `android.util.Log` call,
 * so no log string can survive into the shipped package.
 */
object Logger {

    const val DEFAULT_TAG = "PdfReader"

    fun d(tag: String = DEFAULT_TAG, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun i(tag: String = DEFAULT_TAG, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
    }

    fun w(tag: String = DEFAULT_TAG, message: String) {
        if (BuildConfig.DEBUG) Log.w(tag, message)
    }

    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(tag, message, throwable)
    }
}
