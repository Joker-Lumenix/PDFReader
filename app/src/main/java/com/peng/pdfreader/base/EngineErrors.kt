package com.peng.pdfreader.base

import android.content.Context
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.pdf.PdfError
import com.peng.pdfreader.appcore.pdf.PdfVault

/**
 * Maps the error codes of the PDF engine onto localised messages.
 *
 * The engine itself carries no user facing text, it only reports stable codes.
 * Everything the user gets to read is resolved here.
 */
object EngineErrors {

    /** @return the localised message of the error, empty when there is none. */
    fun localize(context: Context, error: Throwable?): String = when (error?.message) {
        PdfError.CODE_UNREADABLE -> context.getString(R.string.error_pdf_unreadable)
        PdfError.CODE_UNWRITABLE -> context.getString(R.string.error_pdf_unwritable)
        PdfError.CODE_CANCELLED -> context.getString(R.string.error_cancelled)
        PdfError.CODE_STRUCTURE -> context.getString(R.string.error_pdf_structure)
        PdfError.CODE_ENCRYPTED -> context.getString(R.string.error_pdf_encrypted)
        PdfError.CODE_NO_FILES -> context.getString(R.string.error_no_files)
        PdfError.CODE_NO_PAGES -> context.getString(R.string.error_no_pages)
        PdfError.CODE_NO_IMAGES -> context.getString(R.string.error_no_images)
        PdfVault.CODE_WRONG_PASSWORD -> context.getString(R.string.error_wrong_password)
        PdfVault.CODE_NOT_ENCRYPTED -> context.getString(R.string.error_not_encrypted)
        PdfVault.CODE_WEAK_PASSWORD -> context.getString(R.string.error_weak_password)
        PdfVault.CODE_IO -> context.getString(R.string.error_io)
        null -> ""
        else -> error.message.orEmpty()
    }
}
