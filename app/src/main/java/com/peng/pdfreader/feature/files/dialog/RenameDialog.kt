package com.peng.pdfreader.feature.files.dialog

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.widget.BaseBottomSheetDialog
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.databinding.FilesDialogRenameBinding

/**
 * Rename sheet (design: home-PDF-rename).
 *
 * The input starts with the current file name; confirming writes the new name
 * into the sandbox and tells the page that the list has to be refreshed.
 */
class RenameDialog : BaseBottomSheetDialog() {

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

        binding.etName.setText(document.name)
        binding.etName.setSelection(binding.etName.text.length)
        binding.etName.requestFocus()
        binding.btnClear.setOnClickListener { binding.etName.setText("") }
        binding.btnCancel.setOnClickListener { dismissAllowingStateLoss() }
        binding.btnOk.setOnClickListener { confirm(document.id) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun confirm(docId: String) {
        val document = DocumentRepository.get(requireContext()).byId(docId) ?: return
        val newName = binding.etName.text?.toString().orEmpty()
            .replace('/', ' ')
            .trim()
        if (newName.isEmpty() || newName == document.name) {
            dismissAllowingStateLoss()
            return
        }
        DocumentRepository.get(requireContext()).rename(document, newName)
        parentFragmentManager.setFragmentResult(REQUEST_KEY, bundleOf(KEY_DOC_ID to docId))
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
            RenameDialog().apply {
                arguments = bundleOf(ARG_DOC_ID to docId)
            }.show(manager, TAG)
        }
    }
}
