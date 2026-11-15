package com.peng.pdfreader.feature.preview.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.widget.BaseAlertDialog

/**
 * Asks for the text of a note.
 */
class NoteDialog : BaseAlertDialog() {

    /** Raised with the text of the note, empty when the note was cancelled. */
    var onNoteEntered: ((String) -> Unit)? = null

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        val view = inflater.inflate(R.layout.preview_note_dialog, container, false)
        val input = view.findViewById<EditText>(R.id.etNote)
        view.findViewById<TextView>(R.id.tvCancel).setOnClickListener {
            onNoteEntered?.invoke("")
            dismiss()
        }
        view.findViewById<TextView>(R.id.tvSave).setOnClickListener {
            onNoteEntered?.invoke(input.text.toString().trim())
            dismiss()
        }
        return view
    }

    companion object {
        /** Creates the dialog. */
        fun create(): NoteDialog = NoteDialog()
    }
}
