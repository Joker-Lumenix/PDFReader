package com.peng.pdfreader.data.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.peng.pdfreader.R

/**
 * Document formats the app can work with.
 *
 * The labels are intentionally **not** translated - the spec marks the file
 * type names (PDF / Word / Excel / PPT / TXT) as brand level vocabulary.
 */
enum class DocType(
    @StringRes val labelRes: Int,
    @DrawableRes val logoRes: Int,
    val extensions: List<String>,
) {
    PDF(R.string.file_type_pdf, R.mipmap.logo_pdf, listOf("pdf")),
    WORD(R.string.file_type_word, R.mipmap.logo_word, listOf("doc", "docx")),
    EXCEL(R.string.file_type_excel, R.mipmap.logo_xls, listOf("xls", "xlsx")),
    PPT(R.string.file_type_ppt, R.mipmap.logo_ppt, listOf("ppt", "pptx")),
    IMAGE(R.string.image, R.mipmap.logo_pdf, listOf("png", "jpg", "jpeg", "webp")),
    TXT(R.string.file_type_txt, R.mipmap.logo_pdf, listOf("txt", "md")),
    OTHER(R.string.file_type_other, R.mipmap.logo_pdf, listOf("bin", "zip"));

    companion object {
        /** Detects the type from a file name, falling back to [OTHER]. */
        fun of(fileName: String): DocType {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { ext in it.extensions } ?: OTHER
        }

        /** Detects the type from a mime type, falling back to [OTHER]. */
        fun ofMime(mimeType: String?): DocType = when {
            mimeType == null -> OTHER
            mimeType.contains("pdf") -> PDF
            mimeType.contains("word") -> WORD
            mimeType.contains("excel") || mimeType.contains("spreadsheet") -> EXCEL
            mimeType.contains("presentation") || mimeType.contains("powerpoint") -> PPT
            mimeType.contains("image") -> IMAGE
            mimeType.contains("text") -> TXT
            else -> OTHER
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
