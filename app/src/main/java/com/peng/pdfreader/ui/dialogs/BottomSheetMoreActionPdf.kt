package com.peng.pdfreader.ui.dialogs

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.peng.pdfreader.R
import com.peng.pdfreader.base.BaseBottomSheetDialog
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.databinding.FilesSheetFileActionBinding
import com.peng.pdfreader.ui.uiData.FileActions
import com.peng.pdfreader.utils.FileUi
import com.peng.pdfreader.utils.PageCountDrawable

/**
 * The file action sheet of the PDF files (design: home-more).
 *
 * Header with the thumbnail, the name, the meta line and the collect toggle,
 * four quick actions (rename / open with / share / print) and the options card
 * with the five document operations.
 *
 * The sheet never navigates: it reports the picked operation through the
 * fragment result API and the page runs it.
 */
class BottomSheetMoreActionPdf : BaseBottomSheetDialog() {

    private var _binding: FilesSheetFileActionBinding? = null
    private val binding get() = _binding!!

    /** The four quick actions at the top of the sheet. */
    var quickActions: List<Action> = emptyList()

    /** The operations of the options card below them. */
    var optionActions: List<Action> = emptyList()

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FilesSheetFileActionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val doc = DocumentRepository.get(requireContext())
            .byId(requireArguments().getString(ARG_DOC_ID).orEmpty())
        if (doc == null) {
            dismissAllowingStateLoss()
            return
        }
        bindHeader(doc)
        bindActions(doc)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun bindHeader(doc: Document) {
        binding.ivThumbnail.setImageDrawable(thumbnail(doc))
        binding.tvName.text = doc.name
        binding.tvTypeChip.text = getString(doc.type.labelRes)
        binding.tvTypeChip.setTextColor(
            ContextCompat.getColor(requireContext(), FileUi.typeColor(doc.type)),
        )
        binding.tvMeta.text = FileUi.meta(doc)

        binding.ivCollect.setImageResource(
            if (doc.isFavorite) R.mipmap.ic_collect_select else R.mipmap.ic_collect_unselect,
        )
        binding.ivCollect.setOnClickListener { toggleCollect(doc) }
    }

    /** The type logo with the page count chip of the design drawn on top. */
    private fun thumbnail(doc: Document): Drawable? {
        val logo = ContextCompat.getDrawable(requireContext(), doc.type.logoRes) ?: return null
        if (doc.pageCount <= 0) return logo
        return PageCountDrawable(logo, doc.pageCount, requireContext())
    }

    private fun toggleCollect(doc: Document) {
        val repository = DocumentRepository.get(requireContext())
        repository.toggleFavorite(doc)
        val updated = repository.byId(doc.id) ?: doc
        binding.ivCollect.setImageResource(
            if (updated.isFavorite) R.mipmap.ic_collect_select else R.mipmap.ic_collect_unselect,
        )
        parentFragmentManager.setFragmentResult(
            FileActions.REQUEST_KEY,
            bundleOf(
                FileActions.KEY_ACTION to FileActions.CHANGED,
                FileActions.KEY_DOC_ID to updated.id,
            ),
        )
    }

    /**
     * Binds the actions of the caller onto the slots of the design.
     *
     * A slot without an action is hidden, so the sheet also works with fewer
     * actions than the design shows.
     */
    private fun bindActions(doc: Document) {
        val quick = quickActions.ifEmpty { defaultQuickActions() }
        val options = optionActions.ifEmpty { defaultOptionActions() }
        bindQuickActions(doc, quick)
        bindOptionActions(doc, options)
    }

    private fun bindQuickActions(doc: Document, actions: List<Action>) {
        val slots = listOf(
            Triple(binding.actionRename, binding.ivActionRename, binding.tvActionRename),
            Triple(binding.actionOpenWith, binding.ivActionOpenWith, binding.tvActionOpenWith),
            Triple(binding.actionShare, binding.ivActionShare, binding.tvActionShare),
            Triple(binding.actionPrint, binding.ivActionPrint, binding.tvActionPrint),
        )
        slots.forEachIndexed { index, (slot, icon, label) ->
            val action = actions.getOrNull(index)
            if (action == null) {
                slot.visibility = View.GONE
                return@forEachIndexed
            }
            slot.visibility = View.VISIBLE
            icon.setImageResource(action.iconRes)
            label.text = action.label
            slot.setOnClickListener { send(doc, action.id) }
        }
    }

    private fun bindOptionActions(doc: Document, actions: List<Action>) {
        val slots = listOf(
            Triple(binding.rowSplit, binding.ivRowSplit, binding.tvRowSplit),
            Triple(binding.rowCompress, binding.ivRowCompress, binding.tvRowCompress),
            Triple(binding.rowMerge, binding.ivRowMerge, binding.tvRowMerge),
            Triple(binding.rowLock, binding.ivRowLock, binding.tvRowLock),
            Triple(binding.rowDelete, binding.ivRowDelete, binding.tvRowDelete),
        )
        slots.forEachIndexed { index, (slot, icon, label) ->
            val action = actions.getOrNull(index)
            if (action == null) {
                slot.visibility = View.GONE
                return@forEachIndexed
            }
            slot.visibility = View.VISIBLE
            icon.setImageResource(action.iconRes)
            label.text = action.label
            slot.setOnClickListener { send(doc, action.id) }
        }
    }

    private fun send(doc: Document, action: String) {
        parentFragmentManager.setFragmentResult(
            FileActions.REQUEST_KEY,
            bundleOf(
                FileActions.KEY_ACTION to action,
                FileActions.KEY_DOC_ID to doc.id,
            ),
        )
        dismissAllowingStateLoss()
    }

    /** An action of the sheet. */
    data class Action(val id: String, val label: String, val iconRes: Int)

    /** The four quick actions of the design. */
    private fun defaultQuickActions(): List<Action> = listOf(
        Action(FileActions.RENAME, getString(R.string.rename), R.mipmap.ic_rename_red),
        Action(FileActions.OPEN_WITH, getString(R.string.files_open_with), R.mipmap.ic_open_with),
        Action(FileActions.SHARE, getString(R.string.files_share), R.mipmap.ic_share_red),
        Action(FileActions.PRINT, getString(R.string.files_print), R.mipmap.ic_print),
    )

    /** The five operations of the options card of the design. */
    private fun defaultOptionActions(): List<Action> = listOf(
        Action(FileActions.SPLIT, getString(R.string.split), R.mipmap.ic_split_pdf),
        Action(FileActions.COMPRESS, getString(R.string.compress), R.mipmap.ic_compress),
        Action(FileActions.MERGE, getString(R.string.merge), R.mipmap.ic_merge_pdf),
        Action(FileActions.LOCK, getString(R.string.encrypt), R.mipmap.ic_lock),
        Action(FileActions.DELETE, getString(R.string.delete), R.mipmap.ic_delete_red),
    )

    companion object {
        private const val ARG_DOC_ID = "docId"

        /** Shows the action sheet for the given document. */
        fun show(manager: FragmentManager, docId: String) {
            BottomSheetMoreActionPdf().apply {
                arguments = bundleOf(ARG_DOC_ID to docId)
            }.show(manager, TAG)
        }

        /**
         * Shows the action sheet for the given document with the actions of
         * the caller.
         */
        fun show(
            manager: FragmentManager,
            docId: String,
            quickActions: List<Action>,
            optionActions: List<Action>,
        ) {
            BottomSheetMoreActionPdf().apply {
                arguments = bundleOf(ARG_DOC_ID to docId)
                this.quickActions = quickActions
                this.optionActions = optionActions
            }.show(manager, TAG)
        }

        const val TAG = "files.file_action"
    }
}
