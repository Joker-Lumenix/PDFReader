package com.peng.pdfreader.feature.files.util

import android.content.Context
import androidx.core.content.edit
import com.peng.pdfreader.data.prefs.AppPrefs

/**
 * Preferences of the files tab.
 *
 * The type filter and the base sort live in [AppPrefs] because the rest of the
 * app reads them as well. Two settings are only meaningful here: the scope of
 * the list (the tabs of the design) and the direction of the sort.
 */
class FilesPrefs private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val prefs = appContext.getSharedPreferences(
        "pdf_reader_files_prefs",
        Context.MODE_PRIVATE,
    )

    /** The active tab of the list. */
    var scope: FileScope
        get() = runCatching { FileScope.valueOf(prefs.getString(KEY_SCOPE, null).orEmpty()) }
            .getOrDefault(FileScope.ALL)
        set(value) = prefs.edit { putString(KEY_SCOPE, value.name) }

    /** The active sort option - base sort plus direction. */
    var sortOption: FileSortOption
        get() = runCatching {
            FileSortOption.valueOf(prefs.getString(KEY_SORT_OPTION, null).orEmpty())
        }.getOrElse { FileSortOption.of(AppPrefs.get(appContext).fileSort) }
        set(value) = prefs.edit { putString(KEY_SORT_OPTION, value.name) }

    companion object {
        private const val KEY_SCOPE = "files_scope"
        private const val KEY_SORT_OPTION = "files_sort_option"

        @Volatile
        private var instance: FilesPrefs? = null

        fun get(context: Context): FilesPrefs =
            instance ?: synchronized(this) {
                instance ?: FilesPrefs(context).also { instance = it }
            }
    }
}
