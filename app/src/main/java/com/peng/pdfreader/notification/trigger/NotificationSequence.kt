package com.peng.pdfreader.notification.trigger

import android.content.Context
import com.peng.pdfreader.core.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The sequence of the notifications.
 *
 * A scene does not send one notification: it starts the sequence of the design.
 * The functions are used in the order of the strategy - merge, split, lock,
 * unlock, compress, print, scan, image to pdf - every function has its own
 * notification id, so every one of them stays in the notification shade.
 *
 * The sequence repeats itself: as long as no new scene triggers, the same
 * notification is sent again and again, so the content of the notifications
 * stays in the foreground and the user sees it. Only the first notification
 * makes a sound, the repeats are silent.
 *
 * The rules of the sequence come from the file
 * `assets/notification_strategy.json`.
 */
object NotificationSequence {

    private const val TAG = "NotificationSequence"

    /** The loop runs in the process of the app, not in an activity. */
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** The running loop. */
    private var job: Job? = null

    /**
     * The function of the next notification.
     *
     * The order of the strategy decides which campaign is used; the counter
     * moves on with every scene, so the campaigns come one after the other.
     */
    private var step = 0

    /**
     * Starts the sequence for the scene.
     *
     * A scene that triggers while the sequence runs stops it and starts its
     * own: the user sees the newest campaign first.
     */
    fun start(context: Context, scene: NotificationScene) {
        val app = context.applicationContext
        val strategy = NotificationStrategy.load(app)
        val target = strategy.order[step % strategy.order.size]
        step = (step + 1) % strategy.order.size
        job?.cancel()
        job = scope.launch {
            // The first notification of the sequence makes a sound, the
            // repeats are silent.
            NotificationSender.send(app, scene, target, silent = false)
            repeat(strategy.repeatCount.coerceAtLeast(1)) {
                delay(strategy.stepIntervalMs.coerceAtLeast(100L))
                NotificationSender.send(app, scene, target, silent = true)
            }
            Logger.i(TAG, "the sequence of the scene ${scene.key} is over")
        }
        Logger.i(TAG, "the sequence of the scene ${scene.key} starts with ${target.name}")
    }

    /** Stops the sequence, the notifications stay where they are. */
    fun stop() {
        job?.cancel()
        job = null
    }
}
