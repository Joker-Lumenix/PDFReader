package com.peng.pdfreader.ui.adapter.listAdapter

import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.R
import com.peng.pdfreader.databinding.ToolsItemImageRowBinding
import com.peng.pdfreader.feature.tools.util.ToolShapes

/**
 * The picked images as a list (design "Image to PDF下拉").
 *
 * The grid of the design shows the same images as tiles, the list shows them
 * as rows with the name and the size of the file.
 */
class ImageRowAdapter(
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<ImageRowAdapter.Holder>() {

    private val rows = mutableListOf<Row>()

    /** One picked image. */
    data class Row(
        val name: String,
        val meta: String,
        val thumbnail: Bitmap?,
        val isCamera: Boolean = false,
    )

    /** Replaces the content of the list. */
    fun submit(newRows: List<Row>) {
        rows.clear()
        rows.addAll(newRows)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ToolsItemImageRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return Holder(binding)
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(rows[position])
    }

    inner class Holder(private val binding: ToolsItemImageRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: Row) {
            val context = binding.root.context
            if (row.isCamera) {
                // the camera glyph keeps its own size, it is not a picture
                binding.ivThumb.scaleType = ImageView.ScaleType.CENTER
                binding.ivThumb.setImageResource(R.mipmap.ic_camera)
                binding.ivThumb.setColorFilter(Color.WHITE)
                binding.ivThumb.background =
                    ToolShapes.roundRect(context, ToolShapes.CAMERA_TILE, 4f)
            } else {
                binding.ivThumb.scaleType = ImageView.ScaleType.CENTER_CROP
                binding.ivThumb.clearColorFilter()
                binding.ivThumb.setImageBitmap(row.thumbnail)
            }
            binding.tvName.text = row.name
            binding.tvMeta.text = row.meta
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onClick(position)
            }
        }
    }
}
