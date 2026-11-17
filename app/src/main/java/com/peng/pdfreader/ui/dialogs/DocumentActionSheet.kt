package com.peng.pdfreader.ui.dialogs

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.peng.pdfreader.R
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.ui.uiData.FileActions
import com.peng.pdfreader.utils.FileUi

/**
 * Opens the "more" sheet of a document.
 *
 * Only the PDF files use the sheet of the design (design: home-more) with the
 * header, the four quick actions and the option card of the five document
 * operations. Every other format uses the lighter overflow menu of the reader.
 *
 * Both sheets report the picked operation through the fragment result API, so
 * the page that shows the sheet does not have to know which one it is.
 */
object DocumentActionSheet {

    /**
     * Opens the more sheet of the given document.
     *
     * @param context the context the labels of the sheet are read with.
     * @param manager the fragment manager the sheet is shown in.
     * @param document the document the sheet belongs to.
     */
    fun show(context: Context, manager: FragmentManager, document: Document) {
        if (document.type == DocType.PDF) {
            BottomSheetMoreActionPdf.show(manager, document.id)
            return
        }
        val sheet = BottomSheetMoreActionNoPdf.create(
            actions = listOf(
                BottomSheetMoreActionNoPdf.Action(
                    FileActions.RENAME,
                    context.getString(R.string.rename),
                    R.mipmap.ic_rename_black,
                ),
                BottomSheetMoreActionNoPdf.Action(
                    FileActions.OPEN_WITH,
                    context.getString(R.string.files_open_with),
                    R.mipmap.ic_open_with,
                ),
                BottomSheetMoreActionNoPdf.Action(
                    FileActions.SHARE,
                    context.getString(R.string.files_share),
                    R.mipmap.ic_share_square_black,
                ),
                BottomSheetMoreActionNoPdf.Action(
                    FileActions.COMPRESS,
                    context.getString(R.string.compress),
                    R.mipmap.ic_compress,
                ),
            ),
            wideAction = BottomSheetMoreActionNoPdf.Action(
                FileActions.DELETE,
                context.getString(R.string.delete),
                R.mipmap.ic_delete_red,
            ),
            documentName = document.name,
            documentType = context.getString(document.type.labelRes),
            documentMeta = FileUi.meta(document),
            documentLogo = ContextCompat.getDrawable(context, document.type.logoRes),
        )
        sheet.documentId = document.id
        sheet.show(manager, TAG)
    }

    /** The tag of the sheet in the fragment manager. */
    const val TAG = "document.action_sheet"
}
