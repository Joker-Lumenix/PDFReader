package com.peng.pdfreader.ui.dialogs

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.peng.pdfreader.R
import com.peng.pdfreader.base.BaseBottomSheetDialog
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.databinding.FilesDialogRenameBinding

/**
 * Rename sheet (design: home-PDF-rename).
 *
 * The input starts with the current file name; confirming writes the new name
 * into the sandbox and tells the page that the list has to be refreshed.
 */
class BottomSheetRename : BaseBottomSheetDialog() {

    private var _binding: FilesDialogRenameBinding? = null
    private val binding get() = _binding!!

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FilesDialogRenameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val document = DocumentRepository.get(requireContext())
            .byId(requireArguments().getString(ARG_DOC_ID).orEmpty())
        if (document == null) {
            dismissAllowingStateLoss()
            return
        }

        // The design draws the field with a 12dp radius.
        binding.etName.background = GradientDrawable().apply {
            setColor(ContextCompat.getColor(requireContext(), R.color.surface_disabled_alt))
            cornerRadius = dp(12).toFloat()
        }

        // The field carries the name of the file without its suffix, the suffix
        // is added back when the name is confirmed.
        val name = document.name
        val dot = name.lastIndexOf('.')
        val suffix = if (dot > 0) name.substring(dot) else ""
        binding.etName.setText(if (dot > 0) name.substring(0, dot) else name)
        // The whole name is selected, the user types over it.
        binding.etName.selectAll()
        binding.etName.requestFocus()
        binding.btnClear.setOnClickListener { binding.etName.setText("") }
        binding.btnCancel.setOnClickListener { dismissAllowingStateLoss() }
        binding.btnOk.setOnClickListener { confirm(document, suffix) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun confirm(document: Document, suffix: String) {
        val typed = binding.etName.text?.toString()
            .orEmpty()
            .replace('/', ' ')
            .trim()
        if (typed.isEmpty()) {
            dismissAllowingStateLoss()
            return
        }
        val newName = typed + suffix
        if (newName == document.name) {
            dismissAllowingStateLoss()
            return
        }
        // A file of the shared storage can be read but not always written: the
        // rename reports the failure instead of taking the app down.
        val renamed = DocumentRepository.get(requireContext()).rename(document, newName)
        if (!renamed) {
            Toast.makeText(requireContext(), R.string.files_feature_unavailable, Toast.LENGTH_SHORT)
                .show()
            return
        }
        parentFragmentManager.setFragmentResult(REQUEST_KEY, bundleOf(KEY_DOC_ID to document.id))
        dismissAllowingStateLoss()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ARG_DOC_ID = "docId"

        /** Fragment result key of this dialog. */
        const val REQUEST_KEY = "com.peng.pdfreader.files.RENAME"

        /** Key of the renamed document inside the result bundle. */
        const val KEY_DOC_ID = "docId"

        const val TAG = "files.rename"

        /** Shows the rename dialog for the given document. */
        fun show(manager: FragmentManager, docId: String) {
            BottomSheetRename().apply {
                arguments = bundleOf(ARG_DOC_ID to docId)
            }.show(manager, TAG)
        }
    }
}
