package com.peng.pdfreader.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.R
import com.peng.pdfreader.base.BaseBottomSheetDialog

/**
 * Searches the text of a document.
 *
 * The sheet carries a field for the word and the list of the pages that hold
 * it. Every page of the list is a hit with the text around it.
 */
class BottomSheetSearch : BaseBottomSheetDialog() {

    /** Raised with the number of the picked page. */
    var onPageSelected: ((Int) -> Unit)? = null

    /**
     * Raised with the word the user is looking for.
     *
     * The sheet asks for the hits of the word, it does not read the document
     * on its own.
     */
    var onSearch: ((String) -> Unit)? = null

    /** The hits of the search, the number of the page and the text. */
    var hits: List<Hit> = emptyList()
        set(value) {
            field = value
            bindResults()
        }

    /** A hit of the search. */
    data class Hit(val page: Int, val snippet: String)

    private var list: RecyclerView? = null
    private var empty: TextView? = null

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        val view = inflater.inflate(R.layout.preview_search_sheet, container, false)
        val input = view.findViewById<EditText>(R.id.etSearch)
        list = view.findViewById(R.id.list)
        empty = view.findViewById(R.id.tvEmpty)
        list?.layoutManager = LinearLayoutManager(requireContext())
        input.setText(arguments?.getString(ARG_QUERY).orEmpty())
        input.setOnEditorActionListener { _, _, _ ->
            onSearch?.invoke(input.text.toString())
            true
        }
        view.findViewById<TextView>(R.id.tvSearch).setOnClickListener {
            onSearch?.invoke(input.text.toString())
        }
        bindResults()
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        list = null
        empty = null
    }

    /** Shows the hits of the search. */
    private fun bindResults() {
        val recycler = list ?: return
        if (hits.isEmpty()) {
            recycler.visibility = View.GONE
            empty?.visibility = View.VISIBLE
            return
        }
        recycler.visibility = View.VISIBLE
        empty?.visibility = View.GONE
        recycler.adapter = HitAdapter(hits) { index ->
            val hit = hits.getOrNull(index) ?: return@HitAdapter
            onPageSelected?.invoke(hit.page)
            dismiss()
        }
    }

    private class HitAdapter(
        private val hits: List<Hit>,
        private val onClick: (Int) -> Unit,
    ) : RecyclerView.Adapter<HitAdapter.Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.preview_search_item, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val hit = hits[position]
            holder.itemView.findViewById<TextView>(R.id.tvPage).text =
                holder.itemView.context.getString(R.string.preview_search_page, hit.page)
            holder.itemView.findViewById<TextView>(R.id.tvSnippet).text = hit.snippet
            holder.itemView.setOnClickListener { onClick(position) }
        }

        override fun getItemCount(): Int = hits.size

        class Holder(view: View) : RecyclerView.ViewHolder(view)
    }

    companion object {
        /** The key of the word that is searched for. */
        private const val ARG_QUERY = "com.peng.pdfreader.extra.QUERY"

        /** Creates the sheet for the given word. */
        fun create(query: String): BottomSheetSearch = BottomSheetSearch().apply {
            arguments = Bundle().apply { putString(ARG_QUERY, query) }
        }
    }
}
