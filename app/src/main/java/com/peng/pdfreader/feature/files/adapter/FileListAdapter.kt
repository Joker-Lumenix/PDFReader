package com.peng.pdfreader.feature.files.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.appcore.widget.FileItemUiState
import com.peng.pdfreader.appcore.widget.FileListItemView
import com.peng.pdfreader.appcore.widget.createForList
import com.peng.pdfreader.data.model.Document

/**
 * The document list of the files tab.
 *
 * Every row is the one shared template
 * ([com.peng.pdfreader.appcore.widget.FileListItemView]); only the state that
 * is bound to it changes. The adapter is a [ListAdapter], so the list is
 * diffed and only the changed rows are rebound.
 */
class FileListAdapter(
    private val callbacks: Callbacks,
) : ListAdapter<FileListAdapter.Row, FileListAdapter.FileViewHolder>(DIFF) {

    /** One row: the document plus the state that is rendered for it. */
    data class Row(val document: Document, val state: FileItemUiState)

    /** Everything the list can report back to the page. */
    interface Callbacks {
        /** The row itself was tapped. */
        fun onRowClick(document: Document)

        /** The "more" affordance was tapped. */
        fun onRowMore(document: Document)

        /** The selection checkbox was tapped. */
        fun onRowChecked(document: Document, checked: Boolean)

        /** The row was long pressed - this enters the multi select mode. */
        fun onRowLongPressed(document: Document)
    }

    class FileViewHolder(
        private val item: FileListItemView,
    ) : RecyclerView.ViewHolder(item) {

        fun bind(row: Row, callbacks: Callbacks) {
            item.bind(row.state)
            item.onClick = { callbacks.onRowClick(row.document) }
            item.onMoreClick = { callbacks.onRowMore(row.document) }
            item.onCheckedChange = { checked -> callbacks.onRowChecked(row.document, checked) }
            item.setOnLongClickListener {
                callbacks.onRowLongPressed(row.document)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder =
        FileViewHolder(createForList(parent.context))

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position), callbacks)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(oldItem: Row, newItem: Row): Boolean =
                oldItem.document.id == newItem.document.id

            override fun areContentsTheSame(oldItem: Row, newItem: Row): Boolean =
                oldItem.state == newItem.state
        }
    }
}
