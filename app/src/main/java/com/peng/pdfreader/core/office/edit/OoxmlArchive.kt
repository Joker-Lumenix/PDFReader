package com.peng.pdfreader.core.office.edit

import com.peng.pdfreader.core.util.Logger
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A document of the office formats, opened for an edit.
 *
 * The reader of the app only reads the parts of a document, the editor has to
 * write one of them back. The whole package is therefore read into the
 * memory, the part that is changed is replaced and the archive is written
 * again.
 *
 * The order of the parts is kept: the office software expects the
 * `[Content_Types].xml` at the beginning of the archive.
 */
class OoxmlArchive private constructor(private val parts: LinkedHashMap<String, ByteArray>) {

    /** The names of the parts of the archive. */
    val names: List<String> get() = parts.keys.toList()

    /** The bytes of a part, null when the archive does not have it. */
    fun part(name: String): ByteArray? = parts[name]

    /** True when the archive has the part. */
    fun hasPart(name: String): Boolean = parts.containsKey(name)

    /** Replaces a part of the archive. */
    fun replacePart(name: String, bytes: ByteArray) {
        parts[name] = bytes
    }

    /**
     * Writes the archive into the file.
     *
     * The archive is written into a temporary file first, so a failure never
     * leaves a half written document behind.
     */
    fun writeTo(file: File): Boolean {
        val temp = File(file.absolutePath + ".edit")
        val written = runCatching {
            ZipOutputStream(temp.outputStream().buffered()).use { out ->
                for ((name, bytes) in parts) {
                    out.putNextEntry(ZipEntry(name))
                    out.write(bytes)
                    out.closeEntry()
                }
            }
            true
        }.onFailure {
            Logger.e(TAG, "the package of ${file.name} cannot be written", it)
        }.getOrDefault(false)
        if (!written) {
            temp.delete()
            return false
        }
        val replaced = runCatching {
            // The rename replaces the old file on android, the copy is the
            // fallback for the file systems that refuse it.
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
            true
        }.onFailure {
            Logger.e(TAG, "the package of ${file.name} cannot be replaced", it)
        }.getOrDefault(false)
        if (!replaced) temp.delete()
        return replaced
    }

    companion object {
        private const val TAG = "OoxmlArchive"

        /**
         * A document bigger than this is not opened for an edit.
         *
         * The whole package sits in the memory while it is edited, a big
         * document would take the memory of the phone away from the reader.
         */
        private const val MAX_SIZE = 64L * 1024 * 1024

        /** Opens a document of the office formats for an edit. */
        fun open(file: File): OoxmlArchive? {
            if (!file.exists() || !file.isFile) return null
            if (file.length() > MAX_SIZE) {
                Logger.e(TAG, "the document ${file.name} is too big to be edited")
                return null
            }
            return runCatching {
                val parts = LinkedHashMap<String, ByteArray>()
                ZipInputStream(file.inputStream().buffered()).use { input ->
                    while (true) {
                        val entry = input.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val name = entry.name
                        // The archive of a mac book holds the files twice, the
                        // copy in the folder is not part of the document.
                        if (name.startsWith("__MACOSX/")) continue
                        parts[name] = input.readBytes()
                    }
                }
                OoxmlArchive(parts)
            }.onFailure {
                Logger.e(TAG, "the package of ${file.name} cannot be read", it)
            }.getOrNull()
        }
    }
}
