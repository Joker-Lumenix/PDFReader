package com.peng.pdfreader.ui.adapter.listAdapter

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.appcore.util.Thumbnails
import com.peng.pdfreader.appcore.widget.FileItemUiState
import com.peng.pdfreader.appcore.widget.FileListItemView
import com.peng.pdfreader.appcore.widget.createForList
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.utils.PageCountDrawable
import com.peng.pdfreader.ui.uiData.chipColorRes
import com.peng.pdfreader.utils.DocumentMetaFormatter

/**
 * Document list of the home tab.
 *
 * The rows are the one shared template
 * ([FileListItemView]), this adapter only
 * maps a [Document] onto its row state.
 */
class HomeDocumentAdapter(
    /** The file type this list shows, `null` means every type. */
    var filter: DocType? = null,
    private val onOpen: (Document) -> Unit,
    private val onMenu: (Document) -> Unit,
) : ListAdapter<Document, HomeDocumentAdapter.DocumentHolder>(DIFF) {

    /** The type logos with the page count chip of the design. */
    private val logos = mutableMapOf<String, Drawable>()

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
                    thumbnail = thumbnail(context, document),
                    showThumbnail = true,
                    showType = true,
                    showMore = true,
                ),
            )
            item.onClick = { onOpen(document) }
            item.onMoreClick = { onMenu(document) }
        }
    }

    /**
     * The thumbnail of a row.
     *
     * The design draws the type logo with the number of pages on a dark chip
     * in its lower right corner, raster documents show their own picture.
     */
    private fun thumbnail(context: Context, document: Document): Drawable? {
        if (document.type == DocType.IMAGE) {
            val file = DocumentRepository.get(context).fileOf(document)
            val picture = Thumbnails.load(context, file)
            if (picture != null) return picture
        }
        val logo = ContextCompat.getDrawable(context, document.type.logoRes) ?: return null
        if (document.pageCount <= 0) return logo
        val key = "${document.type.name}_${document.pageCount}"
        return logos.getOrPut(key) { PageCountDrawable(logo, document.pageCount, context) }
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
