package com.peng.pdfreader.feature.tools.dialog

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.peng.pdfreader.appcore.widget.BaseBottomSheetDialog
import com.peng.pdfreader.databinding.ToolsDialogTextInputBinding

/**
 * Bottom sheet with a single text field.
 *
 * It is used by the tools that need a name (rename) or a secret (lock and
 * unlock). The confirmed text is delivered through the fragment result API.
 */
class ToolTextInputDialog : BaseBottomSheetDialog() {

    private var binding: ToolsDialogTextInputBinding? = null

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        val view = ToolsDialogTextInputBinding.inflate(inflater, container, false)
        binding = view

        val title = requireArguments().getString(ARG_TITLE).orEmpty()
        val initial = requireArguments().getString(ARG_VALUE).orEmpty()
        val secret = requireArguments().getBoolean(ARG_SECRET, false)

        view.tvTitle.text = title
        view.etInput.setText(initial)
        view.etInput.setSelection(initial.length)
        if (secret) {
            view.etInput.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        view.btnCancel.setOnClickListener { dismiss() }
        view.btnConfirm.setOnClickListener {
            val text = view.etInput.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle().apply {
                putString(RESULT_VALUE, text)
            })
            dismiss()
        }
        return view.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        private const val TAG = "ToolTextInput"
        private const val REQUEST_KEY = "tools.text.input"
        private const val RESULT_VALUE = "value"
        private const val ARG_TITLE = "title"
        private const val ARG_VALUE = "value"
        private const val ARG_SECRET = "secret"

        /**
         * Shows the sheet and reports the confirmed text.
         */
        fun show(
            manager: FragmentManager,
            title: String,
            initial: String = "",
            secret: Boolean = false,
            onConfirmed: (String) -> Unit,
        ) {
            val dialog = ToolTextInputDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putString(ARG_VALUE, initial)
                    putBoolean(ARG_SECRET, secret)
                }
            }
            manager.setFragmentResultListener(REQUEST_KEY, dialog) { _, bundle ->
                val value = bundle.getString(RESULT_VALUE) ?: return@setFragmentResultListener
                onConfirmed(value)
            }
            dialog.show(manager, TAG)
        }
    }
}
