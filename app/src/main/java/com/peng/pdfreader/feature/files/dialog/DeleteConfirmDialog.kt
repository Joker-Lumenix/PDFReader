package com.peng.pdfreader.feature.files.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.widget.BaseAlertDialog
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.databinding.FilesDialogConfirmBinding

/**
 * Delete confirmation.
 *
 * Works for a single document and for a multi selection - the copy differs,
 * the action does not: the confirmed documents are removed from the sandbox.
 */
class DeleteConfirmDialog : BaseAlertDialog() {

    private var _binding: FilesDialogConfirmBinding? = null
    private val binding get() = _binding!!

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FilesDialogConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ids = requireArguments().getStringArrayList(ARG_DOC_IDS).orEmpty()
        if (ids.isEmpty()) {
            dismissAllowingStateLoss()
            return
        }

        binding.tvMessage.text = if (ids.size == 1) {
            getString(R.string.files_delete_message)
        } else {
            getString(R.string.confirm_delete_message)
        }
        binding.btnConfirm.setText(R.string.delete)
        binding.btnCancel.setOnClickListener { dismissAllowingStateLoss() }
        binding.btnConfirm.setOnClickListener { confirm(ids) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun confirm(ids: List<String>) {
        val repository = DocumentRepository.get(requireContext())
        ids.forEach { id ->
            repository.byId(id)?.let { repository.delete(it) }
        }
        parentFragmentManager.setFragmentResult(REQUEST_KEY, bundleOf(KEY_COUNT to ids.size))
        dismissAllowingStateLoss()
    }

    companion object {
        private const val ARG_DOC_IDS = "docIds"

        /** Fragment result key of this dialog. */
        const val REQUEST_KEY = "com.peng.pdfreader.files.DELETE"

        /** Key of the number of deleted documents inside the result bundle. */
        const val KEY_COUNT = "count"

        const val TAG = "files.delete_confirm"

        /** Shows the delete confirmation for the given documents. */
        fun show(manager: FragmentManager, docIds: List<String>) {
            DeleteConfirmDialog().apply {
                arguments = Bundle().apply { putStringArrayList(ARG_DOC_IDS, ArrayList(docIds)) }
            }.show(manager, TAG)
        }
    }
}
