package com.peng.pdfreader.notification.trigger

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.edit
import com.peng.pdfreader.core.util.Logger
import java.util.Calendar

/**
 * The state the notification rules work with.
 *
 * Everything is kept in a private file of the app: the time of the last
 * trigger of every scene and the number of the notifications of the current day.
 */
@SuppressLint("StaticFieldLeak")
class NotificationState private constructor(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /**
     * The time the app was installed.
     *
     * It is the time of the package, not the one of the preferences - the
     * preferences can be cleared without the app being removed.
     */
    val installedAt: Long by lazy {
        runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).firstInstallTime
        }.getOrElse {
            Logger.w(TAG, "the install time is not known: ${it.message}")
            0L
        }
    }

    /** The time of the last trigger of the scene, 0 when it never happened. */
    fun lastTriggerAt(scene: NotificationScene): Long =
        prefs.getLong(KEY_LAST + scene.key, 0L)

    /** Remembers that the scene was triggered. */
    fun markTriggered(scene: NotificationScene, at: Long) {
        prefs.edit {
            putLong(KEY_LAST + scene.key, at)
            putLong(KEY_LAST_ANY, at)
        }
    }

    /** The time of the last trigger of any scene, 0 when there was none. */
    fun lastAnyTriggerAt(): Long = prefs.getLong(KEY_LAST_ANY, 0L)

    /**
     * How many notifications the scene sent today.
     *
     * The counter belongs to the day, so it is back to zero the next day.
     */
    fun dailyCount(scene: NotificationScene): Int {
        resetWhenTheDayChanged(scene)
        return prefs.getInt(KEY_COUNT + scene.key, 0)
    }

    /** Counts one more notification of the scene for today. */
    fun increaseDailyCount(scene: NotificationScene) {
        val day = today()
        resetWhenTheDayChanged(scene)
        prefs.edit {
            putInt(KEY_COUNT + scene.key, prefs.getInt(KEY_COUNT + scene.key, 0) + 1)
            putLong(KEY_DAY + scene.key, day)
        }
    }

    /** Sets the counter of the scene back to zero when a new day started. */
    private fun resetWhenTheDayChanged(scene: NotificationScene) {
        val day = today()
        val stored = prefs.getLong(KEY_DAY + scene.key, day)
        if (stored == day) return
        prefs.edit {
            remove(KEY_COUNT + scene.key)
            putLong(KEY_DAY + scene.key, day)
        }
    }

    /** The day of the year, the counter belongs to it. */
    private fun today(): Long {
        val calendar = Calendar.getInstance()
        return calendar.get(Calendar.YEAR) * 1000L + calendar.get(Calendar.DAY_OF_YEAR)
    }

    companion object {
        private const val TAG = "NotificationState"
        private const val FILE_NAME = "pdf_reader_notification_state"
        private const val KEY_LAST = "last_trigger_"
        private const val KEY_LAST_ANY = "last_trigger_any"
        private const val KEY_COUNT = "daily_count_"
        private const val KEY_DAY = "daily_day_"

        @Volatile
        private var instance: NotificationState? = null

        /** The state of the app. */
        fun get(context: Context): NotificationState =
            instance ?: synchronized(this) {
                instance ?: NotificationState(context).also { instance = it }
            }
    }
}
