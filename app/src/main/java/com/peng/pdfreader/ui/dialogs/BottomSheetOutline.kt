package com.peng.pdfreader.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.R
import com.peng.pdfreader.core.widget.BaseBottomSheetDialog

/**
 * The outline (table of contents) of the document.
 *
 * The entries are handed over as arguments so the sheet survives a rotation.
 */
class BottomSheetOutline : BaseBottomSheetDialog() {

    /** Raised with the index of the tapped entry. */
    var onEntrySelected: ((Int) -> Unit)? = null

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        val view = inflater.inflate(R.layout.preview_outline_sheet, container, false)
        val list = view.findViewById<RecyclerView>(R.id.list)
        val empty = view.findViewById<TextView>(R.id.tvEmpty)
        val titles = arguments?.getStringArrayList(ARG_TITLES).orEmpty()
        val pages = arguments?.getIntArray(ARG_PAGES) ?: IntArray(0)
        if (titles.isEmpty()) {
            empty.visibility = View.VISIBLE
            list.visibility = View.GONE
        } else {
            list.adapter = OutlineAdapter(titles, pages) { index ->
                onEntrySelected?.invoke(index)
                dismiss()
            }
        }
        return view
    }

    private class OutlineAdapter(
        private val titles: List<String>,
        private val pages: IntArray,
        private val onClick: (Int) -> Unit,
    ) : RecyclerView.Adapter<OutlineAdapter.Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.preview_outline_item, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val title = holder.itemView.findViewById<TextView>(R.id.tvTitle)
            val page = holder.itemView.findViewById<TextView>(R.id.tvPage)
            title.text = titles[position]
            page.text = (pages.getOrNull(position) ?: 1).toString()
            holder.itemView.setOnClickListener { onClick(position) }
        }

        override fun getItemCount(): Int = titles.size

        class Holder(view: View) : RecyclerView.ViewHolder(view)
    }

    companion object {
        private const val ARG_TITLES = "com.peng.pdfreader.extra.TITLES"
        private const val ARG_PAGES = "com.peng.pdfreader.extra.PAGES"

        /** Creates the sheet for the given entries. */
        fun create(titles: List<String>, pages: List<Int>): BottomSheetOutline = BottomSheetOutline().apply {
            arguments = Bundle().apply {
                putStringArrayList(ARG_TITLES, ArrayList(titles))
                putIntArray(ARG_PAGES, pages.toIntArray())
            }
        }
    }
}
