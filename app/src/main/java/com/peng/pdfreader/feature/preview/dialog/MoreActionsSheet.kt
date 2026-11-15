package com.peng.pdfreader.feature.preview.dialog

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.widget.BaseBottomSheetDialog

/**
 * The overflow menu of the reader.
 *
 * The rows are described by a stable id and a caption; the host decides what
 * an id means.
 */
class MoreActionsSheet : BaseBottomSheetDialog() {

    /** The rows of the sheet: an id and its caption. */
    var actions: List<Action> = emptyList()

    /** Raised with the id of the tapped row. */
    var onActionSelected: ((String) -> Unit)? = null

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        val view = inflater.inflate(R.layout.preview_more_sheet, container, false)
        val holder = view.findViewById<LinearLayout>(R.id.llActions)
        actions.forEach { action ->
            val row = TextView(requireContext())
            row.text = action.label
            row.setTextColor(requireContext().getColor(R.color.text_secondary))
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            row.setPadding(0, dp(14), 0, dp(14))
            row.isClickable = true
            row.isFocusable = true
            row.setBackgroundResource(android.R.drawable.list_selector_background)
            row.setOnClickListener {
                onActionSelected?.invoke(action.id)
                dismiss()
            }
            holder.addView(row)
        }
        return view
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    /** A row of the sheet. */
    data class Action(val id: String, val label: String)

    companion object {
        /** Creates the sheet for the given rows. */
        fun create(actions: List<Action>): MoreActionsSheet = MoreActionsSheet().apply {
            this.actions = actions
        }
    }
}
