package com.peng.pdfreader.feature.tools.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.widget.FileItemUiState
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.databinding.ToolsItemMergeOrderBinding
import com.peng.pdfreader.feature.tools.util.ToolFormatters

/**
 * The ordered list of the documents that are about to be merged (design
 * "merge-2"). Every row carries the button that removes it from the list.
 */
class MergeOrderAdapter(
    private val onRemove: (Document) -> Unit,
) : RecyclerView.Adapter<MergeOrderAdapter.Holder>() {

    private val documents = mutableListOf<Document>()

    /** Replaces the content of the list. */
    fun submit(newDocuments: List<Document>) {
        documents.clear()
        documents.addAll(newDocuments)
        notifyDataSetChanged()
    }

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
                    meta = ToolFormatters.meta(context, doc),
                    typeLabel = context.getString(doc.type.labelRes),
                    typeColor = context.getColor(R.color.brand_primary_light),
                    thumbnail = ContextCompat.getDrawable(context, doc.type.logoRes),
                    showType = true,
                    showMore = false,
                ),
            )
            binding.item.onClick = {}
            binding.btnRemove.setOnClickListener { document?.let(onRemove) }
        }
    }
}
