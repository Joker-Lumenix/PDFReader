package com.peng.pdfreader.data.prefs

import android.content.Context
import androidx.core.content.edit
import com.peng.pdfreader.data.model.DocType

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

    companion object {
        private const val KEY_LANGUAGE = "language"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_READING_MODE = "reading_mode"
        private const val KEY_FILE_FILTER = "file_filter"
        private const val KEY_FILE_SORT = "file_sort"

        const val DEFAULT_LANGUAGE = "zh-CN"

        @Volatile
        private var instance: AppPrefs? = null

        fun get(context: Context): AppPrefs =
            instance ?: synchronized(this) {
                instance ?: AppPrefs(context).also { instance = it }
            }
    }
}

/** Visual reading mode of the PDF reader. */
enum class ReadingMode {
    LIGHT,
    SEPIA,
    NIGHT_INVERT,
    BLACK,
}

/** Sort order of the file list. */
enum class FileSort {
    NAME,
    MODIFIED,
    CREATED,
    SIZE,
}
