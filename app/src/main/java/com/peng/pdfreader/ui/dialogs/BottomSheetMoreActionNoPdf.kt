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
import com.peng.pdfreader.base.BaseBottomSheetDialog
import com.peng.pdfreader.data.DocumentRepository
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

        bindCollect(view)

        // The four tiles of the design. Every tile shows the operation of the
        // caller: the caption and the icon always belong to the operation, so
        // the shared operations (open with, print) always sit in the same tile.
        TILES.forEachIndexed { index, tile ->
            val container = view.findViewById<LinearLayout>(tile.container)
            val action = actions.getOrNull(index)
            if (action == null) {
                // The tiles without an operation are not shown at all.
                container.visibility = View.GONE
                return@forEachIndexed
            }
            container.visibility = View.VISIBLE
            view.findViewById<ImageView>(tile.icon).setImageResource(action.iconRes)
            view.findViewById<TextView>(tile.label).text = action.label
            container.setOnClickListener { pick(action) }
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

    /**
     * The favourite button of the header.
     *
     * The state comes from the document library, the sheet writes it back and
     * tells the page that the document changed.
     */
    private fun bindCollect(view: View) {
        val button = view.findViewById<ImageView>(R.id.ivCollect)
        val repository = DocumentRepository.get(requireContext())
        val document = documentId.takeIf { it.isNotEmpty() }
            ?.let { repository.byId(it) }
        if (document == null) {
            button.visibility = View.GONE
            return
        }
        button.visibility = View.VISIBLE
        button.setImageResource(
            if (document.isFavorite) R.mipmap.ic_collect_select else R.mipmap.ic_collect_unselect,
        )
        button.setOnClickListener {
            repository.toggleFavorite(document)
            val updated = repository.byId(document.id) ?: document
            button.setImageResource(
                if (updated.isFavorite) {
                    R.mipmap.ic_collect_select
                } else {
                    R.mipmap.ic_collect_unselect
                },
            )
            // The document changed, the list behind the sheet has to know it.
            parentFragmentManager.setFragmentResult(
                FileActions.REQUEST_KEY,
                bundleOf(
                    FileActions.KEY_ACTION to FileActions.CHANGED,
                    FileActions.KEY_DOC_ID to updated.id,
                ),
            )
        }
    }

    private fun pick(action: Action) {
        val callback = onActionSelected
        if (callback != null) {
            callback(action.id)
        } else if (documentId.isNotEmpty()) {
            // The pages of the lists listen to the fragment result API, the
            // sheet reports the operation the same way the sheet of the PDF
            // files does.
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

    /** One tile of the design: the container, the icon and the caption. */
    private data class Tile(
        val container: Int,
        val icon: Int,
        val label: Int,
    )

    companion object {
        /**
         * The four tiles of the design, in the order of the design.
         *
         * The shared operations (open with, print) always sit in the same tile,
         * so the business logic of the pages matches the UI.
         */
        private val TILES = listOf(
            Tile(R.id.actionRename, R.id.ivActionRename, R.id.tvActionRename),
            Tile(R.id.actionOpenWith, R.id.ivActionOpenWith, R.id.tvActionOpenWith),
            Tile(R.id.actionShare, R.id.ivActionShare, R.id.tvActionShare),
            Tile(R.id.actionPrint, R.id.ivActionPrint, R.id.tvActionPrint),
        )

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
