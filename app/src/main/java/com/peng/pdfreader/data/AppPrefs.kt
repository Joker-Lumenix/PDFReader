package com.peng.pdfreader.data

import android.content.Context
import androidx.core.content.edit
import com.peng.pdfreader.core.widget.ReadingMode

/**
 * Persisted application preferences.
 *
 * Everything is kept in a single private SharedPreferences file; nothing is
 * written to shared storage and nothing leaves the device.
 */
class AppPrefs private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)

    /** UI language, an Android locale tag such as `en` or `zh-rTW`. */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        set(value) = prefs.edit { putString(KEY_LANGUAGE, value) }

    /** Whether the screen should stay awake while reading. */
    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = prefs.edit { putBoolean(KEY_KEEP_SCREEN_ON, value) }

    /** Reading mode of the PDF reader. */
    var readingMode: ReadingMode
        get() = runCatching { ReadingMode.valueOf(prefs.getString(KEY_READING_MODE, null) ?: "") }
            .getOrDefault(ReadingMode.LIGHT)
        set(value) = prefs.edit { putString(KEY_READING_MODE, value.name) }

    /** Type filter of the file list, `null` means "all". */
    var fileFilter: DocType?
        get() = prefs.getString(KEY_FILE_FILTER, null)?.let {
            runCatching { DocType.valueOf(it) }.getOrNull()
        }
        set(value) = prefs.edit { putString(KEY_FILE_FILTER, value?.name) }

    /** Sort order of the file list. */
    var fileSort: FileSort
        get() = runCatching { FileSort.valueOf(prefs.getString(KEY_FILE_SORT, null) ?: "") }
            .getOrDefault(FileSort.MODIFIED)
        set(value) = prefs.edit { putString(KEY_FILE_SORT, value.name) }

    /**
     * The number of effective reads of the documents.
     *
     * A read is effective when the user stays with a document for half a
     * minute or turns three pages.
     */
    var effectiveReadCount: Int
        get() = prefs.getInt(KEY_EFFECTIVE_READS, 0)
        set(value) = prefs.edit { putInt(KEY_EFFECTIVE_READS, value) }

    /** The number of effective reads when the default prompt was shown. */
    var defaultPromptReadCount: Int
        get() = prefs.getInt(KEY_DEFAULT_PROMPT_READS, 0)
        set(value) = prefs.edit { putInt(KEY_DEFAULT_PROMPT_READS, value) }

    /** When the default prompt was shown the last time. */
    var defaultPromptShownAt: Long
        get() = prefs.getLong(KEY_DEFAULT_PROMPT_SHOWN_AT, 0L)
        set(value) = prefs.edit { putLong(KEY_DEFAULT_PROMPT_SHOWN_AT, value) }

    /** How often the user pushed the default prompt away. */
    var defaultPromptRefusals: Int
        get() = prefs.getInt(KEY_DEFAULT_PROMPT_REFUSALS, 0)
        set(value) = prefs.edit { putInt(KEY_DEFAULT_PROMPT_REFUSALS, value) }

    /** Whether the guide has already been shown. */
    var guideShown: Boolean
        get() = prefs.getBoolean(KEY_GUIDE_SHOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_GUIDE_SHOWN, value) }

    /** Whether the notification permission was asked for already. */
    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFICATION_ASKED, value) }

    /**
     * The delay of a notification scene, in milliseconds.
     *
     * Null means the default of the scene is used.
     */
    fun notificationDelay(scene: com.peng.pdfreader.notification.trigger.NotificationScene): Long? =
        prefs.getLong(KEY_NOTIFICATION_DELAY + scene.key, -1L).takeIf { it >= 0L }

    /**
     * The campaign of a notification scene.
     *
     * The campaign decides the icon, the picture and the page the
     * notification leads to. Null means the default of the scene is used.
     */
    fun notificationCampaign(
        scene: com.peng.pdfreader.notification.trigger.NotificationScene,
    ): com.peng.pdfreader.notification.trigger.NotificationTarget? {
        val key = KEY_NOTIFICATION_CAMPAIGN + scene.key
        val name = prefs.getString(key, null) ?: return null
        return com.peng.pdfreader.notification.trigger.NotificationTarget.of(name)
    }

    /** How long the app has to be installed before the first notification. */
    var notificationMinMinutesSinceInstall: Int
        get() = prefs.getInt(KEY_NOTIFICATION_MIN_INSTALL, DEFAULT_MIN_MINUTES_SINCE_INSTALL)
        set(value) = prefs.edit { putInt(KEY_NOTIFICATION_MIN_INSTALL, value) }

    /** How long the app waits between two notifications of the same scene. */
    var notificationMinMinutesBetweenScenes: Int
        get() = prefs.getInt(KEY_NOTIFICATION_MIN_SCENE, DEFAULT_MIN_MINUTES_BETWEEN_SCENES)
        set(value) = prefs.edit { putInt(KEY_NOTIFICATION_MIN_SCENE, value) }

    /** How long the app waits between two notifications of any scene. */
    var notificationMinSecondsBetweenScenes: Int
        get() = prefs.getInt(KEY_NOTIFICATION_MIN_ANY, DEFAULT_MIN_SECONDS_BETWEEN_SCENES)
        set(value) = prefs.edit { putInt(KEY_NOTIFICATION_MIN_ANY, value) }

    /** How many notifications one scene may send per day. */
    var notificationDailyLimit: Int
        get() = prefs.getInt(KEY_NOTIFICATION_DAILY_LIMIT, DEFAULT_DAILY_LIMIT)
        set(value) = prefs.edit { putInt(KEY_NOTIFICATION_DAILY_LIMIT, value) }

    /**
     * Whether a notification may be shown without the permission.
     *
     * The media notification of the design is the one that may be shown
     * anyway - the user has to see it to be able to switch the permission on.
     */
    var notificationAllowedWithoutPermission: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_WITHOUT_PERMISSION, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTIFICATION_WITHOUT_PERMISSION, value) }

    companion object {
        private const val KEY_LANGUAGE = "language"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_READING_MODE = "reading_mode"
        private const val KEY_FILE_FILTER = "file_filter"
        private const val KEY_FILE_SORT = "file_sort"
        private const val KEY_GUIDE_SHOWN = "guide_shown"
        private const val KEY_EFFECTIVE_READS = "effective_reads"
        private const val KEY_DEFAULT_PROMPT_READS = "default_prompt_reads"
        private const val KEY_DEFAULT_PROMPT_SHOWN_AT = "default_prompt_shown_at"
        private const val KEY_DEFAULT_PROMPT_REFUSALS = "default_prompt_refusals"
        private const val KEY_NOTIFICATION_ASKED = "notification_permission_asked"
        private const val KEY_NOTIFICATION_DELAY = "notification_delay_"
        private const val KEY_NOTIFICATION_CAMPAIGN = "notification_campaign_"
        private const val KEY_NOTIFICATION_MIN_INSTALL = "notification_min_install_minutes"
        private const val KEY_NOTIFICATION_MIN_SCENE = "notification_min_scene_minutes"
        private const val KEY_NOTIFICATION_MIN_ANY = "notification_min_any_seconds"
        private const val KEY_NOTIFICATION_DAILY_LIMIT = "notification_daily_limit"
        private const val KEY_NOTIFICATION_WITHOUT_PERMISSION = "notification_without_permission"

        const val DEFAULT_LANGUAGE = "zh-CN"

        /** The default limits of the notifications. */
        const val DEFAULT_MIN_MINUTES_SINCE_INSTALL = 10
        const val DEFAULT_MIN_MINUTES_BETWEEN_SCENES = 60
        const val DEFAULT_MIN_SECONDS_BETWEEN_SCENES = 15
        const val DEFAULT_DAILY_LIMIT = 3

        @Volatile
        private var instance: AppPrefs? = null

        fun get(context: Context): AppPrefs =
            instance ?: synchronized(this) {
                instance ?: AppPrefs(context).also { instance = it }
            }
    }
}

/** Sort order of the file list. */
enum class FileSort {
    NAME,
    MODIFIED,
    CREATED,
    SIZE,
}
