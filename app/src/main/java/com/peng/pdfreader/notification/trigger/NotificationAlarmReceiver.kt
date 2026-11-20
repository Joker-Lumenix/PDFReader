package com.peng.pdfreader.notification.trigger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.peng.pdfreader.core.util.Logger

/**
 * The receiver of the alarm that sends a notification.
 *
 * The alarm is the way the app waits for the delay of a scene: it fires even
 * when the process of the app was killed in the meantime.
 */
class NotificationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scene = NotificationScene.of(intent.getStringExtra(NotificationScheduler.EXTRA_SCENE))
            ?: NotificationScene.ALARM
        Logger.i(TAG, "the alarm of the scene ${scene.key} fired")
        // The delay is over, the sequence of the scene starts.
        NotificationSequence.start(context.applicationContext, scene)
    }

    private companion object {
        const val TAG = "NotificationAlarm"
    }
}
