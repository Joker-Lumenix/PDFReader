package com.peng.pdfreader.ui.dialogs

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.os.bundleOf
import com.peng.pdfreader.R
import com.peng.pdfreader.core.widget.BaseBottomSheetDialog
import com.peng.pdfreader.ui.uiData.FileActions

/**
 * The overflow menu of the reader.
 *
 * The sheet follows the design: the header carries the logo, the name and the
 * meta line of the file, four tiles carry the actions and a wide row carries
 * the secondary action.
 */
class BottomSheetMoreActionNoPdf : BaseBottomSheetDialog() {

    /** The tiles of the sheet: an id, a caption and an icon. */
    var actions: List<Action> = emptyList()

    /** The secondary action of the wide row. */
    var wideAction: Action? = null

    /** The name of the file the sheet belongs to. */
    var documentName: String = ""

    /** The type of the file, shown in the chip of the header. */
    var documentType: String = ""

    /** The meta line of the header: the date and the size of the file. */
    var documentMeta: String = ""

    /** The logo of the file. */
    var documentLogo: Drawable? = null

    /**
     * The id of the document the sheet belongs to.
     *
     * When it is set, the picked action is reported through the fragment result
     * API, exactly like the sheet of the PDF files does it. The pages that show
     * the sheet therefore do not have to know how it is built.
     */
    var documentId: String = ""

    /** Raised with the id of the tapped action. */
    var onActionSelected: ((String) -> Unit)? = null

    /**
     * The scrim of the design is a 75 % black layer over the page.
     */
    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(SCRIM_ALPHA)
        }
    }

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        val view = inflater.inflate(R.layout.dialog_file_more_action_nopdf, container, false)

        view.findViewById<TextView>(R.id.tvName).text = documentName
        view.findViewById<TextView>(R.id.tvType).text = documentType
        view.findViewById<TextView>(R.id.tvMeta).text = documentMeta
        view.findViewById<ImageView>(R.id.ivLogo).setImageDrawable(documentLogo)

        val tiles = listOf(
            R.id.ivTile0 to R.id.tvTile0,
            R.id.ivTile1 to R.id.tvTile1,
            R.id.ivTile2 to R.id.tvTile2,
            R.id.ivTile3 to R.id.tvTile3,
        )
        actions.take(tiles.size).forEachIndexed { index, action ->
            val (iconId, labelId) = tiles[index]
            val icon = view.findViewById<ImageView>(iconId)
            icon.setImageResource(action.iconRes)
            icon.setOnClickListener { pick(action) }
            val label = view.findViewById<TextView>(labelId)
            label.text = action.label
            label.setOnClickListener { pick(action) }
        }
        // The tiles without an action are not shown at all.
        tiles.drop(actions.size).forEach { (iconId, labelId) ->
            view.findViewById<ImageView>(iconId).visibility = View.GONE
            view.findViewById<TextView>(labelId).visibility = View.GONE
        }

        val wide = view.findViewById<LinearLayout>(R.id.llWide)
        wide.background = GradientDrawable().apply {
            setColor(WIDE_ROW_COLOR)
            cornerRadius = dp(6.4f)
        }
        val wideAction = wideAction
        if (wideAction == null) {
            wide.visibility = View.GONE
        } else {
            view.findViewById<ImageView>(R.id.ivWide).setImageResource(wideAction.iconRes)
            view.findViewById<TextView>(R.id.tvWide).text = wideAction.label
            wide.setOnClickListener { pick(wideAction) }
        }

        return view
    }

    private fun pick(action: Action) {
        onActionSelected?.invoke(action.id)
        // The pages of the lists listen to the fragment result API, the sheet
        // reports the operation the same way the sheet of the PDF files does.
        if (documentId.isNotEmpty()) {
            parentFragmentManager.setFragmentResult(
                FileActions.REQUEST_KEY,
                bundleOf(
                    FileActions.KEY_ACTION to action.id,
                    FileActions.KEY_DOC_ID to documentId,
                ),
            )
        }
        dismiss()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    /** An action of the sheet. */
    data class Action(val id: String, val label: String, @DrawableRes val iconRes: Int)

    companion object {
        /** The surface of the wide row of the design. */
        private const val WIDE_ROW_COLOR = 0xFFF5F5F5.toInt()

        /** The scrim of the design: 75 % black. */
        private const val SCRIM_ALPHA = 0.75f

        /** Creates the sheet for the given actions. */
        fun create(
            actions: List<Action>,
            wideAction: Action? = null,
            documentName: String = "",
            documentType: String = "",
            documentMeta: String = "",
            documentLogo: Drawable? = null,
        ): BottomSheetMoreActionNoPdf = BottomSheetMoreActionNoPdf().apply {
            this.actions = actions
            this.wideAction = wideAction
            this.documentName = documentName
            this.documentType = documentType
            this.documentMeta = documentMeta
            this.documentLogo = documentLogo
        }
    }
}
