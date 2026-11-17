package com.peng.pdfreader.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.databinding.ToolsDialogDocumentPickerBinding
import com.peng.pdfreader.core.widget.BaseBottomSheetDialog
import com.peng.pdfreader.ui.adapter.listAdapter.DocumentPickAdapter

/**
 * Bottom sheet that lets the user pick a PDF document out of the sandbox.
 *
 * The picked document is delivered through the fragment result API, so the
 * caller never has to hold a reference to the sheet.
 */
class BottomSheetToolDocumentPicker : BaseBottomSheetDialog() {

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        val view = ToolsDialogDocumentPickerBinding.inflate(inflater, container, false)

        val repository = DocumentRepository.get(requireContext())
        val documents = repository.all().filter { it.type == DocType.PDF }
        view.tvEmpty.visibility = if (documents.isEmpty()) View.VISIBLE else View.GONE
        view.rvDocuments.visibility = if (documents.isEmpty()) View.GONE else View.VISIBLE
        view.rvDocuments.layoutManager = LinearLayoutManager(requireContext())
        val adapter = DocumentPickAdapter(
            multiSelect = false,
            onClick = { document ->
                parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle().apply {
                    putString(RESULT_DOC_ID, document.id)
                })
                dismiss()
            },
        )
        view.rvDocuments.adapter = adapter
        adapter.submit(documents)

        return view.root
    }

    companion object {
        private const val TAG = "ToolDocumentPicker"
        private const val REQUEST_KEY = "tools.picker.document"
        private const val RESULT_DOC_ID = "doc_id"

        /**
         * Shows the sheet and reports the picked document.
         */
        fun show(manager: FragmentManager, onPicked: (Document) -> Unit) {
            val dialog = BottomSheetToolDocumentPicker()
            manager.setFragmentResultListener(REQUEST_KEY, dialog) { _, bundle ->
                val id = bundle.getString(RESULT_DOC_ID) ?: return@setFragmentResultListener
                val document = DocumentRepository.get(dialog.requireContext()).byId(id)
                if (document != null) onPicked(document)
            }
            dialog.show(manager, TAG)
        }
    }
}
