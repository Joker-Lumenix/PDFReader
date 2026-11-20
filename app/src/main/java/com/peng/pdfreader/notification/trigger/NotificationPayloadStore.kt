package com.peng.pdfreader.notification.trigger

import android.content.Context
import androidx.core.content.edit

/**
 * The payload of the notification that is waiting to be clicked.
 *
 * A notification with a delay is sent later on, so the payload has to survive
 * the process that prepared it.
 */
object NotificationPayloadStore {

    private const val FILE_NAME = "pdf_reader_notification_payload"

    /** Remembers the payload of the notification that is sent next. */
    fun put(context: Context, payload: NotificationPayload) {
        val prefs = context.applicationContext
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putString(KEY_TITLE, payload.title)
            putString(KEY_TEXT, payload.text)
            putString(KEY_TARGET, payload.target.name)
            putString(KEY_STYLE, payload.style.name)
        }
    }

    /** The payload of the notification that is sent next, null when there is none. */
    fun take(context: Context): NotificationPayload? {
        val prefs = context.applicationContext
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, null) ?: return null
        val text = prefs.getString(KEY_TEXT, null) ?: return null
        val target = NotificationTarget.of(prefs.getString(KEY_TARGET, null))
            ?: NotificationTarget.IMAGE_TO_PDF
        val style = NotificationStyle.of(prefs.getString(KEY_STYLE, null))
            ?: NotificationStyle.NORMAL
        prefs.edit { clear() }
        return NotificationPayload(title, text, target, style)
    }

    private const val KEY_TITLE = "title"
    private const val KEY_TEXT = "text"
    private const val KEY_TARGET = "target"
    private const val KEY_STYLE = "style"
}
