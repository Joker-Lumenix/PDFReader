package com.peng.pdfreader.ui.adapter.listAdapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.widget.FileItemUiState
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.databinding.LockItemFileBinding
import com.peng.pdfreader.ui.widget.LockThumbnailDrawable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The file list of the lock and the unlock flow.
 *
 * The rows are the shared [com.peng.pdfreader.appcore.widget.FileListItemView]
 * of :appcore, the adapter only maps a document onto its state.
 */
class LockFileAdapter(
    private val onFileClick: (Document) -> Unit,
    private val onMoreClick: (Document, View) -> Unit,
) : RecyclerView.Adapter<LockFileAdapter.Holder>() {

    private val documents = mutableListOf<Document>()

    /** Replaces the content of the list. */
    fun submit(items: List<Document>) {
        documents.clear()
        documents.addAll(items)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = documents.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = LockItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding, onFileClick, onMoreClick)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(documents[position])
    }

    /** One file card. */
    class Holder(
        private val binding: LockItemFileBinding,
        private val onFileClick: (Document) -> Unit,
        private val onMoreClick: (Document, View) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var document: Document? = null

        init {
            binding.root.onClick = { document?.let(onFileClick) }
            binding.root.onMoreClick = { document?.let { item -> onMoreClick(item, binding.root) } }
        }

        fun bind(item: Document) {
            document = item
            val context = binding.root.context
            binding.root.bind(
                FileItemUiState(
                    name = item.name,
                    meta = metaOf(context, item),
                    typeLabel = context.getString(item.type.labelRes),
                    typeColor = ContextCompat.getColor(context, R.color.brand_primary_light),
                    thumbnail = LockThumbnailDrawable(
                        context = context,
                        base = ContextCompat.getDrawable(context, item.type.logoRes)!!,
                        pageCount = item.pageCount,
                        locked = item.isEncrypted,
                    ),
                    showType = true,
                    showMore = true,
                ),
            )
        }

        private fun metaOf(context: Context, item: Document): String {
            val date = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(item.modifiedAt))
            return "$date · ${formatSize(item.sizeBytes)}"
        }

        private fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1024.0 / 1024.0)
        }
    }
}
