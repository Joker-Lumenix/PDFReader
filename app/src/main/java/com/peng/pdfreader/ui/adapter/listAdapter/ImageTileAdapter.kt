package com.peng.pdfreader.ui.adapter.listAdapter

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
 * The grid of the images (design "Image to PDF").
 *
 * The first cell of the grid is always the camera tile, it opens the photo
 * picker. Every other cell shows one of the images of the device with its
 * number in the selection.
 */
class ImageTileAdapter(
    private val onAddClick: () -> Unit,
    private val onImageClick: (Int) -> Unit,
) : RecyclerView.Adapter<ImageTileAdapter.Holder>() {

    /** One image of the grid. */
    data class Item(
        /** 图片的缩略图。 */
        val thumbnail: Bitmap?,
        /** 选中的序号，0 表示没有选中。 */
        val index: Int,
    )

    private val items = mutableListOf<Item>()

    /** Replaces the content of the grid. */
    fun submit(newItems: List<Item>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ToolsItemImageTileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        if (position == 0) holder.bindAdd() else holder.bindImage(items[position - 1], position - 1)
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

        fun bindImage(item: Item, index: Int) {
            val context = binding.root.context
            binding.ivCamera.visibility = View.GONE
            binding.ivImage.setBackgroundResource(R.drawable.bg_card_r4)
            binding.ivImage.clearColorFilter()
            binding.ivImage.setImageBitmap(item.thumbnail)
            // 只有选中的图片带编号
            binding.tvIndex.visibility = if (item.index > 0) View.VISIBLE else View.GONE
            binding.tvIndex.text = item.index.toString()
            binding.tvIndex.background = ToolShapes.badge(context, item.index > 0)
            binding.root.setOnClickListener { onImageClick(index) }
        }
    }
}
