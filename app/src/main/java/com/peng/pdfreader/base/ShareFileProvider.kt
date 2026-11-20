package com.peng.pdfreader.base

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.peng.pdfreader.data.DocType
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
                OpenableColumns.DISPLAY_NAME -> displayName(file)
                OpenableColumns.SIZE -> file.length().toString()
                else -> null
            }
        }.toTypedArray()
        val cursor = android.database.MatrixCursor(columns)
        cursor.addRow(values)
        return cursor
    }

    /**
     * The name of the document as the other apps see it.
     *
     * A document of the device is copied into the sandbox before it is
     * shared - the copy carries a prefix that the other apps must not see.
     */
    private fun displayName(file: File): String {
        val name = file.name
        if (name.startsWith(SHARE_PREFIX)) {
            // The copy of a share: the prefix and the time are not part of
            // the name of the document.
            return name.substringAfter('_').substringAfter('_', name)
        }
        // The documents of the sandbox carry the id of the library in front of
        // their name.
        if (name.length > ID_LENGTH && name[ID_LENGTH] == '_') {
            return name.substring(ID_LENGTH + 1)
        }
        return name
    }

    /**
     * The type of the shared file.
     *
     * The system reads it when it builds the preview of a share - with the
     * generic type of a stream the preview of the system stays empty.
     */
    override fun getType(uri: Uri): String {
        val file = resolve(uri) ?: return "application/octet-stream"
        return DocType.mimeTypeOf(file.name) ?: "application/octet-stream"
    }

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

        /**
         * The prefix of the copies that are made for a share.
         *
         * The copies are removed when the app starts again.
         */
        const val SHARE_PREFIX = "share_"

        /** The length of the id that the library puts in front of a name. */
        private const val ID_LENGTH = 36

        /** Builds a shareable uri for a sandbox file. */
        fun uriFor(context: android.content.Context, file: File): Uri = Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .appendPath(file.name)
            .build()
    }
}
