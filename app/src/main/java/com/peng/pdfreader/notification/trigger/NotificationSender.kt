package com.peng.pdfreader.notification.trigger

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.peng.pdfreader.core.util.Logger
import com.peng.pdfreader.notification.NotificationFactory
import com.peng.pdfreader.notification.NotificationPermissionHelper

/**
 * Sends the notification of a scene.
 *
 * Two things are checked before the notification is sent:
 *
 * - the permission of the notifications: without it the notification is only
 *   sent when the configuration allows the media notification
 * - the screen: the notification is sent with a delay, the device may be dark
 *   in the meantime - then the screen is woken up first
 */
object NotificationSender {

    private const val TAG = "NotificationSender"

    /** How long the screen is kept on after it was woken up. */
    private const val WAKE_TIMEOUT_MS = 10_000L

    /** The name of the wake lock of the app. */
    private const val WAKE_LOCK_TAG = "pdfreader:notification"

    /**
     * Sends the notification of the scene.
     *
     * @param target the function whose notification is sent.
     * @param silent the repeat of the sequence is silent, only the first
     *   notification of a sequence makes a sound.
     */
    fun send(
        context: Context,
        scene: NotificationScene,
        target: NotificationTarget,
        silent: Boolean = false,
    ) {
        val app = context.applicationContext
        val config = NotificationConfig.of(app)

        // 1. the permission: without it the notification is only sent when the
        // configuration allows it.
        if (!NotificationPermissionHelper.hasPermission(app) && !config.allowedWithoutPermission) {
            Logger.i(TAG, "the notification of ${target.name} was not sent: no permission")
            return
        }

        // 2. the screen: the device may be dark after the delay.
        if (!isScreenOn(app)) {
            Logger.i(TAG, "the screen is off, it is woken up for the notification")
            wakeScreen(app)
        }

        // 3. the notification itself.
        NotificationFactory.show(app, scene, target, silent)
    }

    /** Whether the screen is on and the device can be used. */
    fun isScreenOn(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            power.isInteractive
        } else {
            @Suppress("DEPRECATION")
            power.isScreenOn
        }
    }

    /**
     * Wakes the screen up.
     *
     * The lock is only held for a moment - the notification itself does not
     * need the screen to stay on.
     */
    @Suppress("DEPRECATION")
    fun wakeScreen(context: Context) {
        val power = context.getSystemService(PowerManager::class.java) ?: return
        runCatching {
            val lock = power.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                WAKE_LOCK_TAG,
            )
            lock.setReferenceCounted(false)
            lock.acquire(WAKE_TIMEOUT_MS)
        }.onFailure {
            Logger.w(TAG, "the screen could not be woken up: ${it.message}")
        }
    }
}
