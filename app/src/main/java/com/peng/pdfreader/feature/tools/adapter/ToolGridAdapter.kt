package com.peng.pdfreader.feature.tools.adapter

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.databinding.ToolsItemToolCardBinding
import com.peng.pdfreader.feature.tools.model.ToolCatalog
import com.peng.pdfreader.feature.tools.model.ToolItem

/**
 * The 2 x 4 grid of the tools tab.
 */
class ToolGridAdapter(
    private val onClick: (ToolItem) -> Unit,
) : RecyclerView.Adapter<ToolGridAdapter.Holder>() {

    private val items: List<ToolItem> = ToolCatalog.items

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ToolsItemToolCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    inner class Holder(private val binding: ToolsItemToolCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ToolItem) {
            binding.ivIcon.setImageResource(item.iconRes)
            binding.tvLabel.setText(item.labelRes)
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}

/**
 * Spacing of a grid whose cells do not fill the whole column.
 *
 * The cells of the design keep a fixed width, the free space of a column is
 * split between the two sides of the cell so that the grid stays centred.
 */
class ToolGridDecoration(
    private val spanCount: Int,
    private val cellWidthPx: Int,
    private val rowSpacingPx: Int,
    private val itemCount: () -> Int,
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val contentWidth = parent.width - parent.paddingStart - parent.paddingEnd
        if (contentWidth <= 0) return

        val column = position % spanCount
        val slot = contentWidth / spanCount
        val slack = (slot - cellWidthPx).coerceAtLeast(0)
        val left = if (spanCount > 1) column * slack / (spanCount - 1) else 0

        outRect.left = left
        outRect.right = slack - left
        outRect.top = 0
        val total = itemCount()
        val lastRow = if (total <= 0) 0 else ((total - 1) / spanCount) * spanCount
        outRect.bottom = if (position >= lastRow) 0 else rowSpacingPx
    }
}
