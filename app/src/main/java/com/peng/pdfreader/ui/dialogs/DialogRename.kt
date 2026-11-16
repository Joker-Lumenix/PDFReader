package com.peng.pdfreader.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.widget.BaseAlertDialog

/**
 * Asks for the new name of a document.
 */
class DialogRename : BaseAlertDialog() {

    /** Raised with the new name, empty when the rename was cancelled. */
    var onNameEntered: ((String) -> Unit)? = null

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        val view = inflater.inflate(R.layout.preview_rename_dialog, container, false)
        val input = view.findViewById<EditText>(R.id.etName)
        input.setText(arguments?.getString(ARG_CURRENT).orEmpty())
        input.setSelection(input.text.length)
        view.findViewById<TextView>(R.id.tvCancel).setOnClickListener {
            onNameEntered?.invoke("")
            dismiss()
        }
        view.findViewById<TextView>(R.id.tvSave).setOnClickListener {
            onNameEntered?.invoke(input.text.toString().trim())
            dismiss()
        }
        return view
    }

    companion object {
        private const val ARG_CURRENT = "com.peng.pdfreader.extra.CURRENT_NAME"

        /** Creates the dialog for the given document name. */
        fun create(currentName: String): DialogRename = DialogRename().apply {
            arguments = Bundle().apply { putString(ARG_CURRENT, currentName) }
        }
    }
}
