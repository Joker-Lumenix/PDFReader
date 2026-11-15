package com.peng.pdfreader.feature.tools.util

import android.content.Context
import com.peng.pdfreader.R
import com.peng.pdfreader.data.model.Document
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formatting helpers of the tool screens.
 */
object ToolFormatters {

    private const val KB = 1024.0
    private const val MB = KB * 1024.0

    /**
     * File size in the unit of the design ("478.1KB", "1.20MB").
     */
    fun size(bytes: Long): String = when {
        bytes >= MB -> String.format(Locale.US, "%.2fMB", bytes / MB)
        bytes >= KB -> String.format(Locale.US, "%.1fKB", bytes / KB)
        else -> "$bytes B"
    }

    /** Modification date in the format of the design ("10-09 16:43"). */
    fun date(timestamp: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

    /** The meta line of a file row: page count, date and size. */
    fun meta(context: Context, doc: Document): String {
        val pages = context.getString(R.string.pages)
        return "$doc.pageCount $pages · ${date(doc.modifiedAt)} · ${size(doc.sizeBytes)}"
    }
}
