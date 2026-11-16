package com.peng.pdfreader.feature.files.sheet

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.widget.BaseBottomSheetDialog
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.databinding.FilesSheetFileActionBinding
import com.peng.pdfreader.feature.files.FileActions
import com.peng.pdfreader.feature.files.util.FileUi
import com.peng.pdfreader.feature.files.util.PageCountDrawable

/**
 * The file action sheet (design: home-more).
 *
 * Header with the thumbnail, the name, the meta line and the collect toggle,
 * four quick actions (rename / open with / share / print) and the options card
 * with the five document operations.
 *
 * Everything except the collect toggle is handed back to the page through the
 * fragment result API; the sheet itself never navigates.
 */
class FileActionSheet : BaseBottomSheetDialog() {

    private var _binding: FilesSheetFileActionBinding? = null
    private val binding get() = _binding!!

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

    private fun bindActions(doc: Document) {
        binding.actionRename.setOnClickListener { send(doc, FileActions.RENAME) }
        binding.actionOpenWith.setOnClickListener { send(doc, FileActions.OPEN_WITH) }
        binding.actionShare.setOnClickListener { send(doc, FileActions.SHARE) }
        binding.actionPrint.setOnClickListener { send(doc, FileActions.PRINT) }
        binding.rowSplit.setOnClickListener { send(doc, FileActions.SPLIT) }
        binding.rowCompress.setOnClickListener { send(doc, FileActions.COMPRESS) }
        binding.rowMerge.setOnClickListener { send(doc, FileActions.MERGE) }
        binding.rowLock.setOnClickListener { send(doc, FileActions.LOCK) }
        binding.rowDelete.setOnClickListener { send(doc, FileActions.DELETE) }
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

    companion object {
        private const val ARG_DOC_ID = "docId"

        /** Shows the action sheet for the given document. */
        fun show(manager: FragmentManager, docId: String) {
            FileActionSheet().apply {
                arguments = bundleOf(ARG_DOC_ID to docId)
            }.show(manager, TAG)
        }

        const val TAG = "files.file_action"
    }
}
