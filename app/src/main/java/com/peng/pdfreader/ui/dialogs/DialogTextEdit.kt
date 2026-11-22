package com.peng.pdfreader.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.peng.pdfreader.R
import com.peng.pdfreader.base.BaseAlertDialog

/**
 * Asks for the new text of a paragraph, of a cell or of a text box.
 *
 * The text of the document is edited in place, the dialog only carries the
 * text that is going to be written into the file.
 */
class DialogTextEdit : BaseAlertDialog() {

    /**
     * Raised with the new text.
     *
     * The value is null when the user closed the dialog without saving.
     */
    var onTextEntered: ((String?) -> Unit)? = null

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        val view = inflater.inflate(R.layout.preview_text_edit_dialog, container, false)
        val input = view.findViewById<EditText>(R.id.etText)
        input.setText(arguments?.getString(ARG_TEXT).orEmpty())
        input.setSelection(input.text.length)
        view.findViewById<TextView>(R.id.tvCancel).setOnClickListener {
            onTextEntered?.invoke(null)
            dismiss()
        }
        view.findViewById<TextView>(R.id.tvSave).setOnClickListener {
            onTextEntered?.invoke(input.text.toString())
            dismiss()
        }
        return view
    }

    companion object {
        /** The key of the text that is edited. */
        private const val ARG_TEXT = "com.peng.pdfreader.extra.EDIT_TEXT"

        /** Creates the dialog for the given text. */
        fun create(text: String): DialogTextEdit = DialogTextEdit().apply {
            arguments = Bundle().apply { putString(ARG_TEXT, text) }
        }
    }
}
