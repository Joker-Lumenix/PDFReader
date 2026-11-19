package com.peng.pdfreader.ui.adapter.listAdapter

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.R
import com.peng.pdfreader.core.widget.FileItemUiState
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.databinding.ToolsItemMergeOrderBinding
import com.peng.pdfreader.feature.tools.util.ToolFormatters
import com.peng.pdfreader.feature.tools.util.ToolShapes
import com.peng.pdfreader.utils.ToolRowThumbnails
import com.peng.pdfreader.utils.PageCountDrawable
import com.peng.pdfreader.ui.widget.LockThumbnailDrawable

/**
 * The ordered list of the documents that are about to be merged (design
 * "merge-2").
 *
 * Every row carries the button that removes it from the list and the handle
 * that starts the drag, the drag itself is done by the screen.
 */
class MergeOrderAdapter(
    private val onRemove: (Document) -> Unit,
    private val onDragStart: (RecyclerView.ViewHolder) -> Unit = {},
    private val thumbnails: ToolRowThumbnails? = null,
) : RecyclerView.Adapter<MergeOrderAdapter.Holder>() {

    private val documents = mutableListOf<Document>()

    /** Replaces the content of the list. */
    fun submit(newDocuments: List<Document>) {
        documents.clear()
        documents.addAll(newDocuments)
        notifyDataSetChanged()
    }

    /** Moves a row to another place of the list. */
    fun move(from: Int, to: Int) {
        if (from !in documents.indices || to !in documents.indices) return
        val document = documents.removeAt(from)
        documents.add(to, document)
        notifyItemMoved(from, to)
    }

    /** The documents of the list, in their current order. */
    fun items(): List<Document> = documents.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ToolsItemMergeOrderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return Holder(binding)
    }

    override fun getItemCount(): Int = documents.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(documents[position])
    }

    inner class Holder(private val binding: ToolsItemMergeOrderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var document: Document? = null

        fun bind(doc: Document) {
            document = doc
            val context = binding.root.context
            binding.item.bind(
                FileItemUiState(
                    name = doc.name,
                    // the design shows the date and the size, the number of
                    // the pages sits on the thumbnail
                    meta = ToolFormatters.metaShort(doc),
                    typeLabel = context.getString(doc.type.labelRes),
                    typeColor = context.getColor(R.color.brand_primary_light),
                    thumbnail = thumbnailOf(doc),
                    showType = true,
                    // the handle of the design takes the place of the "more"
                    // affordance, the text stops in front of it
                    showMore = false,
                    endInsetDp = END_INSET_DP,
                    // the card of the design is a white surface
                    cardBackground = ToolShapes.roundRect(context, SURFACE_CARD, 12f),
                    encrypted = doc.isEncrypted,
                ),
            )
            binding.item.onClick = {}
            // the picture of the first page arrives in the background
            thumbnails?.load(doc, binding.item)
            // the design draws the remove button as a red minus in a circle
            binding.btnRemove.setOnClickListener { document?.let(onRemove) }
            // the handle of the design starts the drag of the row
            binding.ivDrag.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> onDragStart(this)
                    MotionEvent.ACTION_UP -> view.performClick()
                }
                false
            }
        }

        /** The thumbnail of the row: the logo or the blurred page of the vault. */
        private fun thumbnailOf(doc: Document): Drawable? {
            val context = binding.root.context
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
            if (doc.pageCount <= 0) return logo
            return PageCountDrawable(logo, doc.pageCount, context)
        }
    }

    private companion object {
        /** The room the handle of the design needs at the end of the row. */
        const val END_INSET_DP = 24

        /** The colour of the card of the design. */
        const val SURFACE_CARD = 0xFFFFFFFF.toInt()
    }
}
