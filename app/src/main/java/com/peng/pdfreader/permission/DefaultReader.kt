package com.peng.pdfreader.permission

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.peng.pdfreader.core.util.Logger
import com.peng.pdfreader.data.DocType

/**
 * The default handler of the documents of the device.
 *
 * The app can be the app that opens the PDF, the Word, the Excel and the
 * PowerPoint files of the system. The prompt of the app only appears when it
 * is not that app yet.
 */
object DefaultReader {

    /**
     * Whether the app is the handler of the documents of the device.
     *
     * The app is the handler when the system opens the documents with it and
     * another app could open them as well - then the user has picked the app
     * by hand. When the app is the only one that can open the documents,
     * nothing was picked yet and the prompt of the app still helps.
     */
    fun isDefaultHandler(context: Context): Boolean {
        val manager = context.packageManager
        return DocType.entries.any { type -> isDefaultFor(manager, context, type) }
    }

    /** Whether the user has picked the app for the documents of the format. */
    private fun isDefaultFor(
        manager: PackageManager,
        context: Context,
        type: DocType,
    ): Boolean = type.mimeTypes.values.any { mime ->
        val intent = Intent(Intent.ACTION_VIEW).apply {
            // A content uri keeps the file uri rules of Android out of it.
            setDataAndType(Uri.parse("content://documents/1"), mime)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val resolved = manager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolved?.activityInfo?.packageName != context.packageName) return@any false
        // The system opens the document with the app. When another app could
        // open it as well, the user has picked the app by hand.
        val candidates = manager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        candidates.any { it.activityInfo.packageName != context.packageName }
    }

    /**
     * Opens the screen of the system where the default app is picked.
     *
     * Android 12 and above show the page of the default apps of the system,
     * below that the page of the app is opened - there the user picks the
     * default app by hand.
     */
    fun openDefaultAppSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        } else {
            appDetails(context)
        }
        if (!start(context, intent)) {
            // The page of the system is not there, the page of the app is the
            // way out of it.
            start(context, appDetails(context))
        }
    }

    /** The page of the app in the settings of the system. */
    private fun appDetails(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )

    /** Opens the intent, false when the system does not know it. */
    private fun start(context: Context, intent: Intent): Boolean = try {
        // A context that is not an activity (the application for example)
        // needs a task of its own.
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (error: ActivityNotFoundException) {
        Logger.w(TAG, "the screen of the system is not available")
        false
    }

    /** The tag of the log of the helper. */
    private const val TAG = "DefaultReader"
}
