package com.peng.pdfreader.feature.settings.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.peng.pdfreader.MainActivity
import com.peng.pdfreader.R

/**
 * The quiet "always there" notification of the app.
 *
 * It is a plain notification on a low importance channel with the four quick
 * actions of the design; no service is involved, so the user can still swipe
 * it away. A truly persistent notification would need a foreground service -
 * that decision is still open (see the report).
 */
object PersistentNotificationHelper {

    const val CHANNEL_ID = "pdf_reader_persistent"
    const val NOTIFICATION_ID = 4201

    /** Creates the channel, safe to call on every app start. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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
        manager.notify(NOTIFICATION_ID, buildNotification(context))
    }

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

        builder.addAction(
            R.mipmap.ic_print,
            context.getString(R.string.quick_entry_print),
            contentIntent(context, ACTION_PRINT, 1),
        )
        builder.addAction(
            R.mipmap.ic_camera,
            context.getString(R.string.quick_entry_scan),
            contentIntent(context, ACTION_SCAN, 2),
        )
        builder.addAction(
            R.mipmap.ic_lock,
            context.getString(R.string.app_lock),
            contentIntent(context, ACTION_LOCK, 3),
        )
        builder.addAction(
            R.mipmap.ic_compress,
            context.getString(R.string.compress),
            contentIntent(context, ACTION_COMPRESS, 4),
        )
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
