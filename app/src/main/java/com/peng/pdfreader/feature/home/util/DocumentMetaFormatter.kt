package com.peng.pdfreader.feature.home.util

import com.peng.pdfreader.data.model.Document
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats the secondary line of a document row.
 *
 * The design shows `10-09 16:43 · 547 KB`, so the date is month/day plus the
 * clock time and the size switches to MB as soon as it no longer fits into KB.
 */
object DocumentMetaFormatter {

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.getDefault())

    private const val KILOBYTE = 1024L
    private const val MEGABYTE = KILOBYTE * 1024L

    /** `10-09 16:43` */
    fun formatDate(millis: Long): String =
        dateFormatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    /** `547 KB` */
    fun formatSize(bytes: Long): String = when {
        bytes < KILOBYTE -> "$bytes B"
        bytes < MEGABYTE -> "${bytes / KILOBYTE} KB"
        else -> "${bytes / MEGABYTE} MB"
    }

    /** `10-09 16:43 · 547 KB` */
    fun formatMeta(document: Document): String =
        "${formatDate(document.modifiedAt)} · ${formatSize(document.sizeBytes)}"
}
