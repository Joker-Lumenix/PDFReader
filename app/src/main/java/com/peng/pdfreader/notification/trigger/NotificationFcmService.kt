package com.peng.pdfreader.notification.trigger

import android.os.Handler
import android.os.Looper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.peng.pdfreader.R
import com.peng.pdfreader.core.util.Logger

/**
 * The receiver of the messages of firebase.
 *
 * A message may carry the text of the notification, the page it leads to and
 * the design it is built with:
 *
 * - `title`: the title of the notification
 * - `text`: the line of the notification
 * - `target`: the page the notification leads to, one of the names of
 *   [NotificationTarget]
 * - `style`: the design of the notification, `NORMAL` or `BIG`
 *
 * The rules of the app are the same for a message as for every other scene.
 */
class NotificationFcmService : FirebaseMessagingService() {

    /**
     * The token of the device changed.
     *
     * The app does not send the token anywhere, the log shows the new one when
     * the owner wants to see it.
     */
    override fun onNewToken(token: String) {
        Logger.i(TAG, "the token of firebase changed")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data[KEY_TITLE]?.takeIf { it.isNotBlank() } ?: getString(R.string.app_name)
        val text = data[KEY_TEXT]?.takeIf { it.isNotBlank() }
            ?: getString(R.string.notification_promo_text)
        val payload = NotificationPayload(
            title = title,
            text = text,
            target = NotificationTarget.of(data[KEY_TARGET]) ?: NotificationTarget.IMAGE_TO_PDF,
            style = NotificationStyle.of(data[KEY_STYLE]) ?: NotificationStyle.NORMAL,
        )
        Logger.i(TAG, "a message of firebase arrived")
        // The sdk calls the receiver in its own thread, the rules and the
        // notification belong to the main thread.
        val app = applicationContext
        Handler(Looper.getMainLooper()).post {
            NotificationTrigger.triggerWithPayload(app, NotificationScene.FCM, payload)
        }
    }

    private companion object {
        const val TAG = "NotificationFcm"
        const val KEY_TITLE = "title"
        const val KEY_TEXT = "text"
        const val KEY_TARGET = "target"
        const val KEY_STYLE = "style"
    }
}
