package com.peng.pdfreader.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.peng.pdfreader.R

/**
 * The document formats the app can work with.
 *
 * The app is a document reader: it opens the PDF, the Word, the Excel and the
 * PowerPoint files of the device. Every other format - a text file, a picture,
 * an archive - is not supported, so it never reaches the library and it never
 * shows up in the file list.
 *
 * The labels are intentionally **not** translated - the spec marks the file
 * type names (PDF / Word / Excel / PPT) as brand level vocabulary.
 */
enum class DocType(
    @StringRes val labelRes: Int,
    @DrawableRes val logoRes: Int,
    val mimeTypes: Map<String, String>,
) {
    PDF(
        R.string.file_type_pdf,
        R.mipmap.logo_pdf,
        mapOf("pdf" to "application/pdf"),
    ),
    WORD(
        R.string.file_type_word,
        R.mipmap.logo_word,
        mapOf(
            "doc" to "application/msword",
            "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        ),
    ),
    EXCEL(
        R.string.file_type_excel,
        R.mipmap.logo_xls,
        mapOf(
            "xls" to "application/vnd.ms-excel",
            "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        ),
    ),
    PPT(
        R.string.file_type_ppt,
        R.mipmap.logo_ppt,
        mapOf(
            "ppt" to "application/vnd.ms-powerpoint",
            "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        ),
    );

    /** The extensions of the format, without the dot. */
    val extensions: List<String> get() = mimeTypes.keys.toList()

    /**
     * The mime type of the format.
     *
     * The Office formats carry the mime type of the file that is written last
     * - the OOXML one - it is the type the app hands a document over with when
     * the extension of the file does not tell more.
     */
    val mimeType: String get() = mimeTypes.values.last()

    companion object {
        /**
         * Detects the type from a file name.
         *
         * @return the type, or null when the format is not supported.
         */
        fun of(fileName: String): DocType? {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            if (ext.isEmpty()) return null
            return entries.firstOrNull { ext in it.mimeTypes }
        }

        /**
         * Detects the type from a mime type.
         *
         * @return the type, or null when the format is not supported.
         */
        fun ofMime(mimeType: String?): DocType? {
            val mime = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            if (mime.isEmpty()) return null
            return entries.firstOrNull { type -> type.mimeTypes.values.any { it == mime } }
        }

        /**
         * The mime type of a file name.
         *
         * @return the mime type, or null when the format is not supported.
         */
        fun mimeTypeOf(fileName: String): String? {
            val type = of(fileName) ?: return null
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return type.mimeTypes[ext] ?: type.mimeTypes.values.first()
        }

        /**
         * The extension of the format that belongs to a mime type.
         *
         * @return the extension, the first one of the format when the mime
         * type is unknown.
         */
        fun extensionOf(mimeType: String?): String {
            val mime = mimeType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
            val entry = entries.firstNotNullOfOrNull { type ->
                type.mimeTypes.entries.firstOrNull { it.value == mime }
            }
            return entry?.key ?: entries.first().extensions.first()
        }
    }
}

/**
 * A document living in the private on-device sandbox.
 */
data class Document(
    val id: String,
    val name: String,
    val type: DocType,
    val path: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val modifiedAt: Long,
    val isFavorite: Boolean = false,
    val isEncrypted: Boolean = false,
    val pageCount: Int = 0,
    val lastPage: Int = 0,
    val lastReadAt: Long = 0L,
)
