package com.peng.pdfreader.feature.tools.adapter

import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.R
import com.peng.pdfreader.databinding.ToolsItemImageTileBinding
import com.peng.pdfreader.feature.tools.util.ToolShapes

/**
 * The grid of the picked images (design "Image to PDF").
 *
 * The first cell of the grid is always the camera tile, it opens the photo
 * picker. Every other cell shows one of the picked images with its index.
 */
class ImageTileAdapter(
    private val onAddClick: () -> Unit,
    private val onImageClick: (Int) -> Unit,
) : RecyclerView.Adapter<ImageTileAdapter.Holder>() {

    private val images = mutableListOf<Bitmap>()

    /** Replaces the content of the grid. */
    fun submit(newImages: List<Bitmap>) {
        images.clear()
        images.addAll(newImages)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = images.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ToolsItemImageTileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        if (position == 0) holder.bindAdd() else holder.bindImage(position - 1)
    }

    inner class Holder(private val binding: ToolsItemImageTileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bindAdd() {
            val context = binding.root.context
            binding.ivImage.setBackground(ToolShapes.roundRect(context, ToolShapes.CAMERA_TILE, 4f))
            binding.ivImage.setImageDrawable(null)
            // the camera tile of the design carries a 34x34 glyph in its centre
            binding.ivCamera.visibility = View.VISIBLE
            binding.ivCamera.setImageResource(R.mipmap.ic_camera)
            binding.ivCamera.setColorFilter(Color.WHITE)
            binding.tvIndex.visibility = View.GONE
            binding.root.setOnClickListener { onAddClick() }
        }

        fun bindImage(index: Int) {
            val context = binding.root.context
            binding.ivCamera.visibility = View.GONE
            binding.ivImage.setBackgroundResource(R.drawable.bg_card_r4)
            binding.ivImage.clearColorFilter()
            binding.ivImage.setImageBitmap(images[index])
            binding.tvIndex.visibility = View.VISIBLE
            binding.tvIndex.text = (index + 1).toString()
            binding.tvIndex.background = ToolShapes.badge(context, true)
            binding.root.setOnClickListener { onImageClick(index) }
        }
    }
}
