package com.peng.pdfreader.notification.trigger

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.peng.pdfreader.core.util.Logger

/**
 * The delayed notifications.
 *
 * A scene with a delay does not send its notification right away: the app
 * waits with an alarm, so the notification is sent even when the process of
 * the app was killed in the meantime.
 */
object NotificationScheduler {

    private const val TAG = "NotificationScheduler"

    /** Schedules the notification of the scene after the delay. */
    fun scheduleDelayed(context: Context, scene: NotificationScene, delayMillis: Long) {
        val app = context.applicationContext
        val manager = app.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(app, scene)
        val triggerAt = System.currentTimeMillis() + delayMillis
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                // The user does not allow the exact alarms, the notification is
                // a little bit late but it is sent.
                manager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        }.onFailure {
            Logger.e(TAG, "the alarm of the scene ${scene.key} could not be set", it)
        }
    }

    /** Cancels the pending alarms of every scene. */
    fun cancel(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(AlarmManager::class.java) ?: return
        NotificationScene.entries.forEach { scene ->
            runCatching { manager.cancel(pendingIntent(app, scene)) }
        }
    }

    /** The intent that sends the notification of the scene. */
    private fun pendingIntent(context: Context, scene: NotificationScene): PendingIntent {
        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            action = ACTION_SEND
            putExtra(EXTRA_SCENE, scene.key)
        }
        return PendingIntent.getBroadcast(
            context,
            scene.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** The action of the alarm that sends the notification. */
    const val ACTION_SEND = "com.peng.pdfreader.action.NOTIFICATION_ALARM"

    /** The scene of the alarm. */
    const val EXTRA_SCENE = "com.peng.pdfreader.extra.SCENE"
}
