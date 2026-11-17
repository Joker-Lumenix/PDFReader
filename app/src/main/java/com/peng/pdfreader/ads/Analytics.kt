package com.peng.pdfreader.ads

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.peng.pdfreader.appcore.util.Logger

/**
 * The analytics of the app.
 *
 * The events are the ones the owner wants to see in the Firebase console: the
 * start of the app, the opening of a document and the sheets of the design.
 */
object Analytics {

    /** The name of the app in the console. */
    private const val APP_NAME = "pdf_reader"

    private fun analytics(context: Context): FirebaseAnalytics? = runCatching {
        FirebaseAnalytics.getInstance(context.applicationContext)
    }.getOrNull()

    /** The app was opened. */
    fun appOpen(context: Context) {
        analytics(context)?.logEvent("app_open", null)
    }

    /** A document was opened in the reader. */
    fun documentOpened(context: Context, type: String) {
        analytics(context)?.logEvent("document_opened", bundleOf("type" to type))
    }

    /** A sheet of the design was opened. */
    fun sheetOpened(context: Context, name: String) {
        analytics(context)?.logEvent("sheet_opened", bundleOf("sheet" to name))
    }

    /** An error that the user should know about. */
    fun error(context: Context, code: String) {
        Logger.w(TAG, "reported error: $code")
        analytics(context)?.logEvent("error", bundleOf("code" to code))
    }

    private fun bundleOf(vararg pairs: Pair<String, String>) =
        android.os.Bundle().apply {
            pairs.forEach { (key, value) -> putString(key, value) }
        }

    private const val TAG = "Analytics"

    /** The app name, used by the owner to tell the apps apart. */
    const val NAME = APP_NAME
}
