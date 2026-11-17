package com.peng.pdfreader.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.peng.pdfreader.R
import com.peng.pdfreader.core.util.Logger
import com.peng.pdfreader.base.ShareFileProvider
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.ui.uiData.FileActions
import com.peng.pdfreader.ui.dialogs.DialogDeleteConfirm
import com.peng.pdfreader.ui.dialogs.BottomSheetRename
import com.peng.pdfreader.ui.adapter.listAdapter.DocumentPrintAdapter
import com.peng.pdfreader.ui.activities.LockActivity
import com.peng.pdfreader.ui.activities.CompressActivity
import com.peng.pdfreader.ui.activities.MergeActivity
import com.peng.pdfreader.ui.activities.SplitActivity

/**
 * Runs the operation that was picked in the document sheet of the home tab.
 *
 * The sheet (design: home-more) only reports the operation, the running of it
 * stays with the page so that the dialogs, the activities and the list stay in
 * one place.
 */
class HomeDocumentActions(private val host: Fragment) {

    /** Runs the operation of the sheet for the given document. */
    fun run(action: String, docId: String) {
        val context = host.requireContext()
        when (action) {
            FileActions.RENAME -> BottomSheetRename.show(host.childFragmentManager, docId)
            FileActions.DELETE -> DialogDeleteConfirm.show(host.childFragmentManager, listOf(docId))
            FileActions.OPEN_WITH -> openWith(context, docId)
            FileActions.SHARE -> share(context, docId)
            FileActions.PRINT -> printDocument(context, docId)
            FileActions.SPLIT -> SplitActivity.start(context, docId)
            FileActions.COMPRESS -> CompressActivity.start(context, docId)
            FileActions.MERGE -> MergeActivity.start(context)
            FileActions.LOCK -> LockActivity.start(context)
            // The favourite toggle already changed the document, the page
            // refreshes the list on its own.
            FileActions.CHANGED -> Unit
            else -> toast(context)
        }
    }

    private fun openWith(context: Context, docId: String) {
        val document = repository(context).byId(docId) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri(context, document), mimeType(document.type))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (error: Exception) {
            Logger.e(TAG, "no application can open $docId", error)
            toast(context)
        }
    }

    private fun share(context: Context, docId: String) {
        val document = repository(context).byId(docId) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType(document.type)
            putExtra(Intent.EXTRA_STREAM, contentUri(context, document))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.files_share)))
        } catch (error: Exception) {
            Logger.e(TAG, "share of $docId failed", error)
            toast(context)
        }
    }

    private fun printDocument(context: Context, docId: String) {
        val document = repository(context).byId(docId) ?: return
        // The print service rasterises PDFs. The Word / Excel / PowerPoint
        // documents are printed with their text.
        if (document.type == DocType.WORD || document.type == DocType.EXCEL ||
            document.type == DocType.PPT
        ) {
            val printed = OfficePrinting.print(context, repository(context).fileOf(document), document.name)
            if (!printed) toast(context)
            return
        }
        if (document.type != DocType.PDF && document.type != DocType.IMAGE) {
            toast(context)
            return
        }
        val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .build()
        try {
            manager.print(
                document.name,
                DocumentPrintAdapter(repository(context).fileOf(document), document.name),
                attributes,
            )
        } catch (error: Exception) {
            Logger.e(TAG, "print of $docId failed", error)
            toast(context)
        }
    }

    /**
     * The sandbox is private to the app, so a content URI is the only way to
     * hand a document to another application.
     */
    private fun contentUri(context: Context, document: Document): Uri =
        ShareFileProvider.uriFor(context, repository(context).fileOf(document))

    private fun repository(context: Context): DocumentRepository =
        DocumentRepository.get(context)

    private fun toast(context: Context) {
        Toast.makeText(context, R.string.files_feature_unavailable, Toast.LENGTH_SHORT).show()
    }

    private fun mimeType(type: DocType): String = when (type) {
        DocType.PDF -> "application/pdf"
        DocType.WORD -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        DocType.EXCEL -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        DocType.PPT -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        DocType.IMAGE -> "image/*"
        DocType.TXT -> "text/plain"
        DocType.OTHER -> "*/*"
    }

    private companion object {
        const val TAG = "HomeDocumentActions"
    }
}
