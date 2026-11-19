package com.peng.pdfreader.ui.adapter.listAdapter

import android.graphics.drawable.Drawable
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.R
import com.peng.pdfreader.core.widget.FileItemUiState
import com.peng.pdfreader.core.widget.FileListItemView
import com.peng.pdfreader.core.widget.createForList
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.utils.PageCountDrawable
import com.peng.pdfreader.ui.widget.LockThumbnailDrawable
import com.peng.pdfreader.utils.ToolRowThumbnails
import com.peng.pdfreader.feature.tools.util.ToolFormatters

/**
 * The document rows of the tool screens.
 *
 * The row itself is the one shared file list item, the adapter only decides
 * which state of the widget is shown.
 *
 * @param multiSelect the rows carry a selection mark
 * @param selectionAtEnd the selection mark sits on the right of the row, the
 *   design of the merge flow puts it there
 * @param showMore the rows carry the "more" affordance, it is only used when
 *   the row has no selection mark
 * @param thumbnails the pictures of the rows, the design of the merge shows
 *   the first page of the document
 */
class DocumentPickAdapter(
    private val multiSelect: Boolean,
    private val onClick: (Document) -> Unit,
    private val onCheckedChange: ((Document, Boolean) -> Unit)? = null,
    private val selectionAtEnd: Boolean = false,
    private val showMore: Boolean = false,
    private val thumbnails: ToolRowThumbnails? = null,
) : RecyclerView.Adapter<DocumentPickAdapter.Holder>() {

    private val documents = mutableListOf<Document>()
    private val checked = mutableSetOf<String>()

    /** Replaces the content of the list. */
    fun submit(newDocuments: List<Document>) {
        documents.clear()
        documents.addAll(newDocuments)
        notifyDataSetChanged()
    }

    /** Marks a document as selected. */
    fun setChecked(id: String, value: Boolean) {
        if (value) checked.add(id) else checked.remove(id)
        notifyDataSetChanged()
    }

    /** Marks a list of documents as selected. */
    fun setCheckedIds(ids: Set<String>) {
        checked.clear()
        checked.addAll(ids)
        notifyDataSetChanged()
    }

    /** Ids of the documents that are currently selected. */
    fun checkedIds(): Set<String> = checked.toSet()

    /** Clears the selection. */
    fun clearSelection() {
        checked.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(createForList(parent.context))

    override fun getItemCount(): Int = documents.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(documents[position])
    }

    inner class Holder(private val item: FileListItemView) :
        RecyclerView.ViewHolder(item) {

        private var document: Document? = null

        fun bind(doc: Document) {
            document = doc
            val context = item.context
            val isChecked = multiSelect && doc.id in checked
            item.bind(
                FileItemUiState(
                    name = doc.name,
                    // the design shows the date and the size, the number of
                    // the pages sits on the thumbnail
                    meta = ToolFormatters.metaShort(doc),
                    typeLabel = context.getString(doc.type.labelRes),
                    typeColor = context.getColor(R.color.brand_primary_light),
                    thumbnail = thumbnailOf(doc),
                    showType = true,
                    // The design of the selection step carries the selection
                    // mark on the right of every row - the "more" affordance
                    // has no place next to it.
                    showMore = showMore && !multiSelect,
                    showCheckbox = multiSelect,
                    checkboxAtEnd = selectionAtEnd,
                    checked = isChecked,
                    encrypted = doc.isEncrypted,
                ),
            )
            // the picture of the first page arrives in the background
            thumbnails?.load(doc, item)
            item.onClick = { document?.let(onClick) }
            item.onCheckedChange = { value ->
                document?.let { current ->
                    if (value) checked.add(current.id) else checked.remove(current.id)
                    onCheckedChange?.invoke(current, value)
                }
            }
        }

        private fun thumbnailOf(doc: Document): Drawable? {
            val context = item.context
            val logo = ContextCompat.getDrawable(context, doc.type.logoRes) ?: return null
            if (doc.isEncrypted) {
                // An encrypted document shows its blurred page with the lock badge.
                return LockThumbnailDrawable(
                    context = context,
                    base = logo,
                    pageCount = doc.pageCount,
                    locked = true,
                )
            }
            // the rows of the design carry the page count chip on the thumbnail
            if (doc.pageCount <= 0) return logo
            return PageCountDrawable(logo, doc.pageCount, context)
        }
    }
}
