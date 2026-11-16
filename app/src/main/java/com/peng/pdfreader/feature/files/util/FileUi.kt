package com.peng.pdfreader.feature.files.util

import androidx.annotation.ColorRes
import com.peng.pdfreader.R
import com.peng.pdfreader.data.model.DocType
import com.peng.pdfreader.data.model.Document
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formatting helpers shared by the list, the action sheet and the dialogs.
 *
 * All values come from the design: the meta line reads
 * `10-09 16:43 · 547 KB`.
 */
object FileUi {

    private const val DATE_PATTERN = "MM-dd HH:mm"

    /** The separator between the date and the size of a meta line. */
    const val SEPARATOR = " · "

    /** The secondary line of a file row. */
    fun meta(doc: Document): String = date(doc.modifiedAt) + SEPARATOR + size(doc.sizeBytes)

    fun date(timestamp: Long): String =
        SimpleDateFormat(DATE_PATTERN, Locale.getDefault()).format(Date(timestamp))

    /** Human readable file size. */
    fun size(bytes: Long): String = when {
        bytes < 1024L -> "${bytes}B"
        bytes < 1024L * 1024L -> "${bytes / 1024L}KB"
        bytes < 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.1fMB", bytes / 1024.0 / 1024.0)
        else -> String.format(Locale.getDefault(), "%.1fGB", bytes / 1024.0 / 1024.0 / 1024.0)
    }

    /** Colour of the type pill, per the design (PDF / Excel / PPT / Word). */
    @ColorRes
    fun typeColor(type: DocType): Int = when (type) {
        DocType.PDF -> R.color.brand_primary_light
        DocType.WORD -> R.color.text_link
        DocType.EXCEL -> R.color.text_success
        DocType.PPT -> R.color.text_warning
        else -> R.color.text_secondary
    }
}
