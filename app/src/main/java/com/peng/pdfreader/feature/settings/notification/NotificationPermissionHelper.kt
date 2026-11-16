package com.peng.pdfreader.feature.settings.notification

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.data.prefs.AppPrefs

/**
 * The notification permission of the app, with the version handling of it.
 *
 * - Android 13 (API 33) and above: the permission is a runtime permission, the
 *   system dialog is asked for. As soon as the user denies it twice the system
 *   refuses to show the dialog again - from that moment on the custom sheet of
 *   the design takes over.
 * - Below Android 13: there is no runtime permission at all, the notifications
 *   can only be switched off in the settings of the system. The custom sheet
 *   of the design guides the user there.
 */
object NotificationPermissionHelper {

    private const val TAG = "NotificationPermission"

    /**
     * Whether the runtime permission has to be asked for.
     *
     * Only Android 13 and above know the permission at all.
     */
    fun requiresRuntimeRequest(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Whether the app may post notifications.
     *
     * On Android 13 and above the runtime permission decides, below that the
     * switch of the system settings does.
     */
    fun hasPermission(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.areNotificationsEnabled()
    }

    /** Whether the runtime permission itself was granted. */
    fun isRuntimePermissionGranted(context: Context): Boolean {
        if (!requiresRuntimeRequest()) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Whether the system dialog can still be shown.
     *
     * The system stops showing it as soon as the user denied the permission
     * twice; before the first request the dialog is of course available too.
     *
     * @param asked whether the permission was asked for already
     */
    fun canAskSystem(activity: Activity, asked: Boolean): Boolean {
        if (!requiresRuntimeRequest()) return false
        if (!asked) return true
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    /** Remembers that the permission was asked for, so the state survives. */
    fun markAsked(context: Context) {
        AppPrefs.get(context).notificationPermissionAsked = true
    }

    /** Whether the permission was asked for already. */
    fun wasAsked(context: Context): Boolean =
        AppPrefs.get(context).notificationPermissionAsked

    /**
     * Opens the notification settings of the app.
     *
     * It is the way out for the versions below Android 13 and for the moment
     * the system refuses to show its own dialog.
     */
    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        try {
            context.startActivity(intent)
        } catch (error: Exception) {
            Logger.e(TAG, "the notification settings are not available", error)
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }
    }
}
