package com.peng.pdfreader.base

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/**
 * Grants temporary read access to a sandbox file when the user shares it.
 *
 * The document stays inside the private sandbox - only the explicitly shared
 * file is exposed, and only for as long as the receiving app holds the grant.
 */
class ShareFileProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val file = resolve(uri) ?: return null
        if (!file.exists()) return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val file = resolve(uri) ?: return null
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val values = columns.map { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> file.name
                OpenableColumns.SIZE -> file.length()
                else -> null
            }
        }
        val cursor = android.database.MatrixCursor(columns)
        cursor.addRow(values.toTypedArray())
        return cursor
    }

    override fun getType(uri: Uri): String = "application/octet-stream"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun resolve(uri: Uri): File? {
        val name = uri.lastPathSegment ?: return null
        val root = context?.filesDir ?: return null
        // The sandbox holds the documents in filesDir/documents, the provider
        // only ever exposes files from there.
        val sandbox = File(root, "documents")
        val candidate = File(sandbox, name)
        // Guard against path traversal - only files directly in the sandbox.
        return if (candidate.parentFile == sandbox) candidate else null
    }

    companion object {
        /** Authority declared in the manifest. */
        const val AUTHORITY = "com.peng.pdfreader.fileprovider"

        /** Builds a shareable uri for a sandbox file. */
        fun uriFor(context: android.content.Context, file: File): Uri =
            Uri.parse("content://$AUTHORITY/${file.name}")
    }
}
