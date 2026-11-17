package com.peng.pdfreader.utils

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import com.peng.pdfreader.core.office.OfficeText
import com.peng.pdfreader.ui.adapter.listAdapter.OfficeTextPrintAdapter
import java.io.File

/**
 * Prints a Word / Excel / PowerPoint document.
 *
 * The print service of the system rasterises PDFs, so the text of the document
 * is printed instead of its layout.
 */
object OfficePrinting {

    /**
     * Prints the document.
     *
     * @return true when the print job was started.
     */
    fun print(context: Context, file: File, jobName: String): Boolean {
        val lines = OfficeText.lines(file)
        if (lines.isEmpty()) return false
        val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: return false
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .build()
        manager.print(jobName, OfficeTextPrintAdapter(jobName, lines), attributes)
        return true
    }
}
