package com.peng.pdfreader.notification.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.peng.pdfreader.core.util.Logger

/**
 * The receiver of the event "the device was unlocked".
 *
 * The event is sent by the system when the user unlocks the device - the
 * scene decides whether a notification is sent.
 */
class NotificationUnlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return
        Logger.i(TAG, "the device was unlocked")
        NotificationTrigger.trigger(context.applicationContext, NotificationScene.DEVICE_UNLOCK)
    }

    private companion object {
        const val TAG = "NotificationUnlock"
    }
}
