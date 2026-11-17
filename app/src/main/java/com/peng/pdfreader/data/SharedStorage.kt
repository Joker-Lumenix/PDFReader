package com.peng.pdfreader.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.peng.pdfreader.core.util.Logger
import java.io.File

/**
 * The documents of the shared storage of the device.
 *
 * The app asks for the access to all files, so it can list the documents the
 * user keeps on the device. The files stay where they are - the app only
 * remembers them, the preview and the tools work on the original file.
 */
object SharedStorage {

    /** How deep the scan goes into a folder. */
    private const val MAX_DEPTH = 6

    /** The most files one scan collects. */
    private const val MAX_FILES = 3_000

    /**
     * Whether the app may read the shared storage.
     *
     * On Android 11 and above that is the "access all files" permission, below
     * that the runtime permission for reading the external storage.
     */
    fun canRead(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * The files of the shared storage the app can open.
     *
     * The scan walks the whole tree of the shared storage, so it is a little
     * expensive - it belongs on a worker thread.
     */
    fun scan(): List<SharedFile> {
        val root = Environment.getExternalStorageDirectory() ?: return emptyList()
        if (!root.isDirectory) {
            Logger.w(TAG, "the shared storage ${root.path} cannot be read")
            return emptyList()
        }
        val found = mutableListOf<SharedFile>()
        collect(root, 1, found)
        Logger.i(TAG, "the scan of ${root.path} found ${found.size} documents")
        return found
    }

    /** Walks a folder and its sub folders. */
    private fun collect(folder: File, depth: Int, found: MutableList<SharedFile>) {
        if (depth > MAX_DEPTH || found.size >= MAX_FILES) return
        val children = folder.listFiles() ?: return
        children.forEach { child ->
            if (found.size >= MAX_FILES) return@forEach
            when {
                child.isDirectory -> {
                    val name = child.name
                    // Hidden folders and the private app storage of Android 11
                    // are not part of the documents of the user.
                    if (name.startsWith(".")) return@forEach
                    if (name.equals("Android", ignoreCase = true)) return@forEach
                    collect(child, depth + 1, found)
                }

                child.isFile -> addFile(child, found)
            }
        }
    }

    /**
     * Adds a file to the result when the app can work with its type.
     *
     * The pictures are not part of the document list of the app - they belong
     * to the image to pdf tool, which picks them on its own.
     */
    private fun addFile(file: File, found: MutableList<SharedFile>) {
        if (file.length() <= 0L) return
        val type = DocType.of(file.name)
        if (type == DocType.OTHER || type == DocType.IMAGE) return
        found += SharedFile(file, type)
    }

    /** The tag of the log of the scanner. */
    private const val TAG = "SharedStorage"
}

/**
 * One document of the shared storage.
 *
 * @param file the file itself, it stays where the user put it
 * @param type the type of the document
 */
data class SharedFile(
    val file: File,
    val type: DocType,
)
