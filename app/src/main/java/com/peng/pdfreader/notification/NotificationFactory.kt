package com.peng.pdfreader.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.peng.pdfreader.R
import com.peng.pdfreader.core.util.Logger
import com.peng.pdfreader.notification.trigger.NotificationPayload
import com.peng.pdfreader.notification.trigger.NotificationPayloadStore
import com.peng.pdfreader.notification.trigger.NotificationScene
import com.peng.pdfreader.notification.trigger.NotificationStyle
import com.peng.pdfreader.notification.trigger.NotificationTarget
import com.peng.pdfreader.notification.trigger.NotificationConfig
import com.peng.pdfreader.ui.activities.SplashActivity

/**
 * Builds and sends the notifications of the app.
 *
 * The two designs of the owner are used: the small notification with the icon
 * and the button, and the big one with the picture of the campaign. The click
 * on the notification opens the start up screen, it shows the ad and hands the
 * event over to the page the notification leads to.
 */
object NotificationFactory {

    private const val TAG = "NotificationFactory"

    /** The channel of the notifications of the design. */
    const val CHANNEL_ID = "pdf_reader_promo"

    /** The first id of the notifications, every scene has its own. */
    private const val NOTIFICATION_ID_BASE = 4300

    /** The action of the intent that opens the start up screen. */
    const val ACTION_NOTIFICATION = "com.peng.pdfreader.action.NOTIFICATION"

    /** The scene that sent the notification. */
    const val EXTRA_SCENE = "com.peng.pdfreader.extra.NOTIFICATION_SCENE"

    /** The page the notification leads to. */
    const val EXTRA_TARGET = "com.peng.pdfreader.extra.NOTIFICATION_TARGET"

    /** Creates the channel, safe to call on every notification. */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.app_name),
            // The high importance makes the notification show up as a banner,
            // so the user sees it even when the app is in the foreground.
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notification_rationale)
            // The banner is enough, the notification stays quiet.
            setSound(null, null)
            enableVibration(false)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Sends the notification of the scene.
     *
     * The payload of the notification comes from firebase when there is one,
     * otherwise the campaign of the step is used - the campaign decides the
     * icon, the picture and the page the notification leads to.
     *
     * @param target the function whose notification is sent.
     * @param silent the repeat of the sequence is silent - only the first
     *   notification of a sequence makes a sound.
     */
    fun show(
        context: Context,
        scene: NotificationScene,
        target: NotificationTarget,
        silent: Boolean = false,
    ) {
        val app = context.applicationContext
        val manager = app.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(app)
        // A message of firebase carries its own notification, it is used for
        // the first step of the sequence.
        val payload = NotificationPayloadStore.take(app) ?: defaultPayload(app, scene, target)
        val notification = build(app, scene, payload, silent)
        manager.notify(payload.target.notifyId, notification)
        Logger.i(TAG, "the notification of ${payload.target.name} was sent")
    }

    /** Removes the notification of the target. */
    fun cancel(context: Context, target: NotificationTarget) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(target.notifyId)
    }

    /** The id of the notification of the scene. */
    fun notificationId(scene: NotificationScene): Int = NOTIFICATION_ID_BASE + scene.ordinal

    /**
     * The campaign of the step.
     *
     * The step decides which notification is shown: the icon, the picture and
     * the page the notification leads to. A message of firebase overwrites it.
     */
    private fun defaultPayload(
        context: Context,
        scene: NotificationScene,
        target: NotificationTarget,
    ): NotificationPayload = NotificationPayload(
        title = context.getString(R.string.app_name),
        text = context.getString(R.string.notification_promo_text),
        target = target,
        style = NotificationStyle.BIG,
    )

    private fun build(
        context: Context,
        scene: NotificationScene,
        payload: NotificationPayload,
        silent: Boolean,
    ): Notification {
        val click = clickIntent(context, scene, payload)
        val normal = RemoteViews(context.packageName, R.layout.notification_normal)
        normal.setImageViewResource(R.id.ivImage, payload.target.iconRes)
        normal.setTextViewText(R.id.tvDescription, payload.text)
        normal.setOnClickPendingIntent(R.id.tvTryNow, click)

        val big = RemoteViews(context.packageName, R.layout.notification_big)
        big.setImageViewResource(R.id.ivImage, payload.target.imageRes)
        big.setTextViewText(R.id.tvDescription, payload.text)
        big.setOnClickPendingIntent(R.id.tvTryNow, click)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(payload.title)
            .setContentText(payload.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            // The notification disappears as soon as it is clicked.
            .setAutoCancel(true)
            .setContentIntent(click)
            .setCustomContentView(normal)
            // Only the first notification of a sequence makes a sound, the
            // repeats are silent.
            .setSilent(silent)

        // The big notification of the design carries the picture of the
        // campaign; the small one is only the line with the icon.
        if (payload.style == NotificationStyle.BIG) {
            builder.setCustomBigContentView(big)
        }
        return builder.build()
    }

    /**
     * The intent that opens the start up screen.
     *
     * The start up screen shows the ad and hands the event over to the page of
     * the notification.
     */
    private fun clickIntent(
        context: Context,
        scene: NotificationScene,
        payload: NotificationPayload,
    ): PendingIntent {
        val intent = Intent(context, SplashActivity::class.java).apply {
            action = ACTION_NOTIFICATION
            putExtra(EXTRA_SCENE, scene.key)
            putExtra(EXTRA_TARGET, payload.target.name)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            notificationId(scene),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
