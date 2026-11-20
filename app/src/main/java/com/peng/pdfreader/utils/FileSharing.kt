package com.peng.pdfreader.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.peng.pdfreader.base.ShareFileProvider
import com.peng.pdfreader.core.util.Logger
import com.peng.pdfreader.data.DocType
import java.io.File

/**
 * Hands a document of the app to another app.
 *
 * The sandbox cannot be addressed with a `file://` uri, so the file is shared
 * through the file provider of the app. A document of the device lives outside
 * of the sandbox - it is copied into it before it is shared.
 */
object FileSharing {

    /**
     * A document that is handed over to another app.
     *
     * @param file the file of the document
     * @param mimeType the mime type of the document
     */
    data class DocumentFile(val file: File, val mimeType: String)

    /**
     * A document that is shared with another app.
     *
     * @param uri the uri of the file
     * @param name the name of the document
     * @param mimeType the mime type of the document
     */
    data class ShareItem(val uri: Uri, val name: String, val mimeType: String)

    /**
     * Shares one or more documents with the other apps of the device.
     *
     * The uris travel in the extra of the stream - the system builds the
     * clip data of the share from it and hands the permission to read the
     * files to the app that is chosen.
     *
     * @param context the context of the app
     * @param documents the documents that are shared
     * @param title the title of the chooser of the system
     */
    fun share(context: Context, documents: List<DocumentFile>, title: String?) {
        if (documents.isEmpty()) return
        val items = documents.map { document ->
            val file = copyIntoSandbox(context, document.file)
            ShareItem(
                uri = ShareFileProvider.uriFor(context, file),
                name = document.file.name,
                mimeType = document.mimeType,
            )
        }
        val uris = items.map { it.uri }
        // One file travels as ACTION_SEND, more than one as
        // ACTION_SEND_MULTIPLE - the share sheet of the system only shows the
        // list of the files for the second one.
        val intent = Intent(
            if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE,
        ).apply {
            type = sharedType(items)
            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
            putExtra(Intent.EXTRA_TITLE, items.first().name)
            putExtra(Intent.EXTRA_SUBJECT, items.first().name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, title)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
    }

    /**
     * The type of the share.
     *
     * The type of the files when they are all of the same kind, otherwise the
     * type that covers them all.
     */
    private fun sharedType(items: List<ShareItem>): String {
        val first = items.first().mimeType
        return if (items.all { it.mimeType == first }) first else "*/*"
    }

    /**
     * Shares one document with the other apps of the device.
     *
     * @param context the context of the app
     * @param file the file of the document
     * @param mimeType the mime type of the document
     * @param title the title of the chooser of the system
     */
    fun share(context: Context, file: File, mimeType: String, title: String? = null) {
        share(context, listOf(DocumentFile(file, mimeType)), title)
    }

    /**
     * Copies a file of the device into the sandbox.
     *
     * The provider of the app only hands out the files of the sandbox, so a
     * document of the device is copied into it before it is shared. The copy
     * carries a prefix and is removed when the app starts again.
     */
    private fun copyIntoSandbox(context: Context, file: File): File {
        val sandbox = File(context.filesDir, "documents")
        if (file.parentFile?.canonicalPath == sandbox.canonicalPath) return file
        val target = File(sandbox, "${ShareFileProvider.SHARE_PREFIX}${System.currentTimeMillis()}_${file.name}")
        val copied = runCatching {
            file.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target.length() == file.length()
        }.getOrDefault(false)
        if (!copied) {
            Logger.w(TAG, "the file ${file.name} could not be copied into the sandbox")
            target.delete()
            return file
        }
        return target
    }

    /**
     * Opens the document with another app of the device.
     *
     * A document of the device is copied into the sandbox first - the
     * provider of the app only hands out the files of the sandbox.
     */
    fun openWith(context: Context, file: File, mimeType: String): Boolean {
        val shared = copyIntoSandbox(context, file)
        val uri = ShareFileProvider.uriFor(context, shared)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /**
     * The mime type of a document, derived from its extension.
     *
     * Only the formats the app supports have a mime type - a file of another
     * format is never handed over to another app.
     *
     * @return the mime type, or null when the format is not supported.
     */
    fun mimeTypeOf(fileName: String): String? = DocType.mimeTypeOf(fileName)

    private fun contentUri(context: Context, file: File): Uri? = runCatching {
        if (!file.exists()) return null
        val sandbox = File(context.filesDir, "documents")
        if (file.parentFile?.canonicalPath != sandbox.canonicalPath) return null
        ShareFileProvider.uriFor(context, file)
    }.getOrNull()
    /** The tag of the log of the helper. */
    private const val TAG = "FileSharing"
}
