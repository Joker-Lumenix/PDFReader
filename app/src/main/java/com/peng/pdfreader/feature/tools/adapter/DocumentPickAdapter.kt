package com.peng.pdfreader.feature.tools.adapter

import android.graphics.drawable.Drawable
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.widget.FileItemUiState
import com.peng.pdfreader.appcore.widget.FileListItemView
import com.peng.pdfreader.appcore.widget.createForList
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.feature.files.util.PageCountDrawable
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
 * @param showMore the rows carry the "more" affordance, it replaces the
 *   selection mark on the rows that are not selected
 */
class DocumentPickAdapter(
    private val multiSelect: Boolean,
    private val onClick: (Document) -> Unit,
    private val onCheckedChange: ((Document, Boolean) -> Unit)? = null,
    private val selectionAtEnd: Boolean = false,
    private val showMore: Boolean = false,
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
                    meta = ToolFormatters.meta(context, doc),
                    typeLabel = context.getString(doc.type.labelRes),
                    typeColor = context.getColor(R.color.brand_primary_light),
                    thumbnail = thumbnailOf(doc),
                    showType = true,
                    // the design shows the "more" affordance on the rows that
                    // are not selected, the selection mark takes its place
                    showMore = showMore && !isChecked,
                    showCheckbox = multiSelect && isChecked,
                    checkboxAtEnd = selectionAtEnd,
                    checked = isChecked,
                ),
            )
            item.onClick = { document?.let(onClick) }
            item.onCheckedChange = { value ->
                document?.let { current ->
                    if (value) checked.add(current.id) else checked.remove(current.id)
                    onCheckedChange?.invoke(current, value)
                }
            }
        }

        private fun thumbnailOf(doc: Document): Drawable? {
            val logo = ContextCompat.getDrawable(item.context, doc.type.logoRes) ?: return null
            // the rows of the design carry the page count chip on the thumbnail
            if (doc.pageCount <= 0) return logo
            return PageCountDrawable(logo, doc.pageCount, item.context)
        }
    }
}
