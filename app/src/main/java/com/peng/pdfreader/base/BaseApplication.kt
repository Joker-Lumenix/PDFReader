package com.peng.pdfreader.base

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.data.AppPrefs
import com.peng.pdfreader.data.DocumentRepository

/**
 * Base application of the app.
 *
 * It only carries the initialisation that **every app of this type needs**:
 *
 * - forcing the light theme (the design ships no dark scheme)
 * - synchronising the stored language before the first frame
 * - warming up the shared preference store and the document sandbox
 * - installing the crash handler
 *
 * Feature specific initialisation does not belong here - it goes into the
 * feature that needs it.
 */
abstract class BaseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        AppPrefs.get(this)
        DocumentRepository.get(this)
        installCrashHandler()
    }

    /**
     * Installs the crash handler.
     *
     * Debug builds record the stack trace, release builds forward it to the
     * platform default handler.
     */
    private fun installCrashHandler() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            Logger.e(TAG, "uncaught exception on ${thread.name}", error)
            default?.uncaughtException(thread, error)
        }
    }

    companion object {
        private const val TAG = "PdfReader"

        /** Convenience accessor for the application context. */
        fun context(context: Context): Context = context.applicationContext
    }
}
