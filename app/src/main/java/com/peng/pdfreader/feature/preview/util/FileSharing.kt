package com.peng.pdfreader.feature.preview.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.peng.pdfreader.base.ShareFileProvider
import java.io.File

/**
 * Hands a document of the private sandbox to another app.
 *
 * The sandbox cannot be addressed with a `file://` uri, so the file is shared
 * through the file provider of the app. When the file does not live in the
 * sandbox the name of the document is shared instead - that never crashes.
 */
object FileSharing {

    /** Shares the document with the other apps of the device. */
    fun share(context: Context, file: File, mimeType: String) {
        val uri = contentUri(context, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            if (uri != null) {
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                putExtra(Intent.EXTRA_TEXT, file.name)
            }
        }
        val chooser = Intent.createChooser(intent, null)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
    }

    /** Opens the document with another app of the device. */
    fun openWith(context: Context, file: File, mimeType: String): Boolean {
        val uri = contentUri(context, file) ?: return false
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** The mime type of a document, derived from its extension. */
    fun mimeTypeOf(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "txt", "md" -> "text/plain"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "application/octet-stream"
    }

    private fun contentUri(context: Context, file: File): Uri? = runCatching {
        if (!file.exists()) return null
        val sandbox = File(context.filesDir, "documents")
        if (file.parentFile?.canonicalPath != sandbox.canonicalPath) return null
        ShareFileProvider.uriFor(context, file)
    }.getOrNull()
}
