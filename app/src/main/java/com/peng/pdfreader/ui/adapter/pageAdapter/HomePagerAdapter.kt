package com.peng.pdfreader.ui.adapter.pageAdapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.widget.FileListItemDecoration
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.databinding.HomeItemDocumentPageBinding
import com.peng.pdfreader.ui.adapter.listAdapter.HomeDocumentAdapter

/**
 * The pages of the home tab.
 *
 * The home shows one page per file type (All, PDF, Word, Excel, PPT); the
 * pages can be swiped horizontally and the filter strip above follows the
 * current page.
 */
class HomePagerAdapter(
    private val onOpen: (Document) -> Unit,
    private val onMenu: (Document) -> Unit,
    private val onEmptyAction: () -> Unit,
) : RecyclerView.Adapter<HomePagerAdapter.PageHolder>() {

    /** The filter of every page, in the order of the filter strip. */
    val filters: List<DocType?> = listOf(null, DocType.PDF, DocType.WORD, DocType.EXCEL, DocType.PPT)

    private var documents: List<Document> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder =
        PageHolder(
            HomeItemDocumentPageBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
        )

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        holder.bind(position, filters[position])
    }

    override fun getItemCount(): Int = filters.size

    /** Hands the documents to every page. */
    fun submit(documents: List<Document>) {
        this.documents = documents
        notifyDataSetChanged()
    }

    /** The documents of one page. */
    fun documentsOf(position: Int): List<Document> {
        val filter = filters.getOrNull(position) ?: return documents
        return documents.filter { it.type == filter }
    }

    inner class PageHolder(
        private val binding: HomeItemDocumentPageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val adapter = HomeDocumentAdapter(
            filter = null,
            onOpen = onOpen,
            onMenu = onMenu,
        )

        init {
            binding.documentList.layoutManager = LinearLayoutManager(binding.root.context)
            binding.documentList.adapter = adapter
            binding.documentList.setHasFixedSize(true)
            // The design leaves 12dp between two rows.
            val gap = (ROW_GAP * binding.root.resources.displayMetrics.density).toInt()
            binding.documentList.addItemDecoration(FileListItemDecoration(gap))
            bindEmptyState()
        }

        /** The empty state of the design (home-emp). */
        private fun bindEmptyState() {
            binding.emptyState.tvTitle.setText(R.string.home_empty_title)
            binding.emptyState.tvHint.setText(R.string.home_empty_hint)
            binding.emptyState.tvAction.setText(R.string.home_empty_action)
            binding.emptyState.tvAction.setOnClickListener { onEmptyAction() }
        }

        fun bind(position: Int, filter: DocType?) {
            adapter.filter = filter
            val page = documentsOf(position)
            adapter.submitList(page)
            binding.emptyState.root.visibility =
                if (page.isEmpty()) View.VISIBLE else View.GONE
            binding.documentList.visibility =
                if (page.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    companion object {
        /** The gap between two rows of the design. */
        private const val ROW_GAP = 12
    }
}
