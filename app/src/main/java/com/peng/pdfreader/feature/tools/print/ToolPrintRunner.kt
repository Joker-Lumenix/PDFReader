package com.peng.pdfreader.feature.tools.print

import android.print.PrintAttributes
import android.print.PrintManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.pdf.PdfEngine
import com.peng.pdfreader.data.Document
import java.io.File

/**
 * Starts a print job for a document of the sandbox.
 */
object ToolPrintRunner {

    /**
     * Prints the given document with the framework print service.
     */
    fun print(activity: FragmentActivity, document: Document) {
        val file = File(document.path)
        if (!file.isFile) {
            Toast.makeText(activity, R.string.tools_print_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val printManager = activity.getSystemService(PrintManager::class.java)
        if (printManager == null) {
            Toast.makeText(activity, R.string.tools_print_no_printer, Toast.LENGTH_SHORT).show()
            return
        }
        val adapter = PdfPrintDocumentAdapter(file, PdfEngine.pageCount(file))
        runCatching {
            printManager.print(
                document.name,
                adapter,
                PrintAttributes.Builder().build(),
            )
        }.onFailure {
            Toast.makeText(activity, R.string.tools_print_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /** Prints the given document with the framework print service. */
    fun print(fragment: Fragment, document: Document) =
        print(fragment.requireActivity() as FragmentActivity, document)
}
