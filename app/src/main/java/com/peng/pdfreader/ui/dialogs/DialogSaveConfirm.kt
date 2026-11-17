package com.peng.pdfreader.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.peng.pdfreader.R
import com.peng.pdfreader.core.widget.BaseAlertDialog
import com.peng.pdfreader.databinding.FilesDialogConfirmBinding

/**
 * Save confirmation.
 *
 * A plain confirmation shown before a modified document is written back to the
 * sandbox. It does not write anything itself - the page that shows it decides
 * what "confirmed" means.
 */
class DialogSaveConfirm : BaseAlertDialog() {

    private var _binding: FilesDialogConfirmBinding? = null
    private val binding get() = _binding!!

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = FilesDialogConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvMessage.text = requireArguments().getString(ARG_MESSAGE)
            ?: getString(R.string.files_save_message)
        binding.btnConfirm.setText(R.string.save)
        binding.btnCancel.setOnClickListener { dismissAllowingStateLoss() }
        binding.btnConfirm.setOnClickListener { confirm() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun confirm() {
        parentFragmentManager.setFragmentResult(REQUEST_KEY, bundleOf(KEY_CONFIRMED to true))
        dismissAllowingStateLoss()
    }

    companion object {
        private const val ARG_MESSAGE = "message"

        /** Fragment result key of this dialog. */
        const val REQUEST_KEY = "com.peng.pdfreader.files.SAVE_CONFIRM"

        /** Key of the confirmation flag inside the result bundle. */
        const val KEY_CONFIRMED = "confirmed"

        const val TAG = "files.save_confirm"

        /** Shows the save confirmation. */
        fun show(manager: FragmentManager, message: String? = null) {
            DialogSaveConfirm().apply {
                arguments = bundleOf(ARG_MESSAGE to message)
            }.show(manager, TAG)
        }
    }
}
