package com.peng.pdfreader.notification.trigger

import android.content.Context
import com.peng.pdfreader.core.util.Logger

/**
 * The entry point of the notifications.
 *
 * Every scene of the app goes through [trigger]: the rules are checked, the
 * delay of the scene is waited out and the notification is sent. The state of
 * the app (the rules, the delays and the counters) lives in the preferences,
 * so the owner can change the values without a new release.
 */
object NotificationTrigger {

    private const val TAG = "NotificationTrigger"

    /**
     * A scene was triggered.
     *
     * The rules decide whether the notification is sent at all; when the scene
     * has a delay, the notification is sent later on.
     */
    fun trigger(context: Context, scene: NotificationScene) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val state = NotificationState.get(app)
        val config = NotificationConfig.of(app)

        // 1. the global rules: the app must not be in the foreground and the
        // last notification must be a moment ago.
        val global = NotificationRules.globalBlocker(app, state, scene, now, config)
        if (global != null) {
            Logger.i(TAG, "the scene ${scene.key} was dropped: $global")
            return
        }

        // 2. the rules of the scene: the install time, the interval of the
        // scene and its daily limit.
        val own = NotificationRules.sceneBlocker(state, scene, now, config)
        if (own != null) {
            Logger.i(TAG, "the scene ${scene.key} was dropped: $own")
            return
        }

        // The scene counts from now on, the delay is part of the trigger.
        state.markTriggered(scene, now)
        // The daily limit counts the scenes, not the notifications of the
        // sequence.
        state.increaseDailyCount(scene)

        val delay = config.delayOf(scene)
        if (delay <= 0L) {
            NotificationSequence.start(app, scene)
        } else {
            NotificationScheduler.scheduleDelayed(app, scene, delay)
        }
    }

    /**
     * A scene was triggered with a payload of firebase.
     *
     * The payload carries its own text, its own campaign and its own design;
     * the rules are the same for every scene.
     */
    fun triggerWithPayload(context: Context, scene: NotificationScene, payload: NotificationPayload) {
        NotificationPayloadStore.put(context, payload)
        trigger(context, scene)
    }

    /**
     * The notification was clicked.
     *
     * The loop of the timer is stopped right away, so the notification is not
     * raised again while the user is looking at the page it opened.
     */
    fun onNotificationClicked(context: Context, scene: NotificationScene) {
        val app = context.applicationContext
        NotificationScheduler.cancel(app)
        NotificationTimerService.stop(app)
        // The sequence stops as well, the notification is not raised again
        // while the user is looking at the page it opened.
        NotificationSequence.stop()
        Logger.i(TAG, "the notification of the scene ${scene.key} was clicked")
    }

    /**
     * Schedules a one shot alarm.
     *
     * It is the notification of the scene "alarm": the alarm fires once and
     * sends the notification.
     */
    fun scheduleAlarm(context: Context, delayMillis: Long) {
        NotificationScheduler.scheduleDelayed(
            context.applicationContext,
            NotificationScene.ALARM,
            delayMillis,
        )
    }
}
