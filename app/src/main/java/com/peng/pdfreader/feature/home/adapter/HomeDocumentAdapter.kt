package com.peng.pdfreader.feature.home.adapter

import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.appcore.widget.FileItemUiState
import com.peng.pdfreader.appcore.widget.FileListItemView
import com.peng.pdfreader.appcore.widget.createForList
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.feature.home.model.chipColorRes
import com.peng.pdfreader.feature.home.util.DocumentMetaFormatter

/**
 * Document list of the home tab.
 *
 * The rows are the one shared template
 * ([com.peng.pdfreader.appcore.widget.FileListItemView]), this adapter only
 * maps a [Document] onto its row state.
 */
class HomeDocumentAdapter(
    private val onOpen: (Document) -> Unit,
    private val onMenu: (Document) -> Unit,
) : ListAdapter<Document, HomeDocumentAdapter.DocumentHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentHolder =
        DocumentHolder(createForList(parent.context))

    override fun onBindViewHolder(holder: DocumentHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DocumentHolder(
        private val item: FileListItemView,
    ) : RecyclerView.ViewHolder(item) {

        fun bind(document: Document) {
            val context = item.context
            item.bind(
                FileItemUiState(
                    name = document.name,
                    meta = DocumentMetaFormatter.formatMeta(document),
                    typeLabel = context.getString(document.type.labelRes),
                    typeColor = ContextCompat.getColor(context, document.type.chipColorRes()),
                    thumbnail = ContextCompat.getDrawable(context, document.type.logoRes),
                    showThumbnail = true,
                    showType = true,
                    showMore = true,
                ),
            )
            item.onClick = { onOpen(document) }
            item.onMoreClick = { onMenu(document) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Document>() {
            override fun areItemsTheSame(oldItem: Document, newItem: Document): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Document, newItem: Document): Boolean =
                oldItem == newItem
        }
    }
}
