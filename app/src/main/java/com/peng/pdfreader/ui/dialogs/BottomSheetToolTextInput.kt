package com.peng.pdfreader.ui.dialogs

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.peng.pdfreader.core.widget.BaseBottomSheetDialog
import com.peng.pdfreader.databinding.ToolsDialogTextInputBinding
import com.peng.pdfreader.feature.tools.util.ToolShapes

/**
 * Bottom sheet with a single text field.
 *
 * It is used by the tools that need a name (rename, save) or a secret (lock and
 * unlock). The confirmed text is delivered through the fragment result API.
 */
class BottomSheetToolTextInput : BaseBottomSheetDialog() {

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
        // the input of the design is #f4f4f4 with a radius of 12
        view.etInput.background = ToolShapes.roundRect(
            requireContext(),
            ToolShapes.INPUT_FIELD,
            12f,
        )
        if (secret) {
            view.etInput.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        view.ivClear.setOnClickListener { view.etInput.setText("") }
        view.btnCancel.setOnClickListener {
            parentFragmentManager.setFragmentResult(REQUEST_KEY_CANCEL, Bundle())
            dismiss()
        }
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
        private const val REQUEST_KEY_CANCEL = "tools.text.input.cancel"
        private const val RESULT_VALUE = "value"
        private const val ARG_TITLE = "title"
        private const val ARG_VALUE = "value"
        private const val ARG_SECRET = "secret"

        /**
         * Shows the sheet and reports the confirmed text.
         *
         * @param onCancelled called when the user closes the sheet without a value
         */
        fun show(
            manager: FragmentManager,
            title: String,
            initial: String = "",
            secret: Boolean = false,
            onCancelled: (() -> Unit)? = null,
            onConfirmed: (String) -> Unit,
        ) {
            val dialog = BottomSheetToolTextInput().apply {
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
            if (onCancelled != null) {
                manager.setFragmentResultListener(REQUEST_KEY_CANCEL, dialog) { _, _ ->
                    onCancelled()
                }
            }
            dialog.show(manager, TAG)
        }
    }
}
