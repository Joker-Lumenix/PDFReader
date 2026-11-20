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
import com.peng.pdfreader.ui.activities.MainActivity
import com.peng.pdfreader.R

/**
 * The quiet "always there" notification of the app.
 *
 * It is a plain notification on a low importance channel; the expanded view
 * carries the four quick actions of the design - print, scan, lock and
 * compress - as a row of icons with their labels. No service is involved, so
 * the user can still swipe it away.
 */
object PersistentNotificationHelper {

    const val CHANNEL_ID = "pdf_reader_persistent"
    const val NOTIFICATION_ID = 4201

    /** Creates the channel, safe to call on every app start. */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.quick_entry),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_rationale)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /** Posts the notification with the quick actions of the design. */
    fun show(context: Context) {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, notification(context))
    }

    /**
     * The notification of the design.
     *
     * The timer of the notifications uses it as the notification of its
     * foreground service, so the user always sees the same entry.
     */
    fun notification(context: Context): Notification = buildNotification(context)

    /** Removes the notification again. */
    fun hide(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(NOTIFICATION_ID)
    }

    private fun buildNotification(context: Context): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.offline_badge))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(contentIntent(context, ACTION_OPEN, 0))

        // The row of the design: four entries with an icon and a label.
        val views = RemoteViews(context.packageName, R.layout.notification_always)
        views.setTextViewText(R.id.tvQuickPrint, context.getString(R.string.quick_entry_print))
        views.setTextViewText(R.id.tvQuickScan, context.getString(R.string.quick_entry_scan))
        views.setTextViewText(R.id.tvQuickLock, context.getString(R.string.app_lock))
        views.setTextViewText(R.id.tvQuickCompress, context.getString(R.string.compress))
        views.setOnClickPendingIntent(R.id.quickPrint, contentIntent(context, ACTION_PRINT, 1))
        views.setOnClickPendingIntent(R.id.quickScan, contentIntent(context, ACTION_SCAN, 2))
        views.setOnClickPendingIntent(R.id.quickLock, contentIntent(context, ACTION_LOCK, 3))
        views.setOnClickPendingIntent(R.id.quickCompress, contentIntent(context, ACTION_COMPRESS, 4))
        builder.setCustomBigContentView(views)

        return builder.build()
    }

    private fun contentIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    const val ACTION_OPEN = "com.peng.pdfreader.action.OPEN"
    const val ACTION_PRINT = "com.peng.pdfreader.action.PRINT"
    const val ACTION_SCAN = "com.peng.pdfreader.action.SCAN"
    const val ACTION_LOCK = "com.peng.pdfreader.action.LOCK"
    const val ACTION_COMPRESS = "com.peng.pdfreader.action.COMPRESS"
}
