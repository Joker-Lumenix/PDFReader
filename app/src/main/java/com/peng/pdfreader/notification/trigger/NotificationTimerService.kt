package com.peng.pdfreader.notification.trigger

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.peng.pdfreader.core.util.Logger
import com.peng.pdfreader.notification.PersistentNotificationHelper

/**
 * The timer of the notifications.
 *
 * The service wakes the app up every minute and lets the scene "timer" do its
 * work. It runs in the foreground, so the system does not stop it - the
 * notification of the service is the one of the design that is always there.
 *
 * The service stops as soon as the user clicks a notification, so the
 * notification is not raised again while the user is looking at the page it
 * opened.
 */
class NotificationTimerService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val task = object : Runnable {
        override fun run() {
            NotificationTrigger.trigger(this@NotificationTimerService, NotificationScene.TIMER)
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        PersistentNotificationHelper.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            PersistentNotificationHelper.NOTIFICATION_ID,
            PersistentNotificationHelper.notification(this),
        )
        handler.removeCallbacks(task)
        handler.postDelayed(task, INTERVAL_MS)
        Logger.i(TAG, "the timer of the notifications is running")
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(task)
        Logger.i(TAG, "the timer of the notifications was stopped")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NotificationTimer"

        /** How often the timer looks at the rules, in milliseconds. */
        const val INTERVAL_MS = 60_000L

        /** Starts the timer. */
        fun start(context: Context) {
            val intent = Intent(context, NotificationTimerService::class.java)
            runCatching {
                context.startForegroundService(intent)
            }.onFailure {
                Logger.e(TAG, "the timer could not be started", it)
            }
        }

        /** Stops the timer. */
        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, NotificationTimerService::class.java))
            }.onFailure {
                Logger.w(TAG, "the timer could not be stopped: ${it.message}")
            }
        }
    }
}
