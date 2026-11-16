package com.peng.pdfreader.ui.adapter.listAdapter

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.databinding.ToolsItemSplitPageBinding
import com.peng.pdfreader.feature.tools.util.ToolShapes

/**
 * The page grid of the split screen (design "Split PDF").
 *
 * Every tile shows the rendered thumbnail of the page and the number badge in
 * its top left corner, the badge turns green as soon as the page is selected.
 */
class SplitPageAdapter(
    private val onToggle: (Int) -> Unit,
) : RecyclerView.Adapter<SplitPageAdapter.Holder>() {

    private val pages = mutableListOf<Page>()
    private val selected = mutableSetOf<Int>()

    /** One page of the document. */
    data class Page(val number: Int, val thumbnail: Bitmap?)

    /** Replaces the content of the grid. */
    fun submit(newPages: List<Page>) {
        pages.clear()
        pages.addAll(newPages)
        notifyDataSetChanged()
    }

    /** Marks a page as selected. */
    fun setSelected(number: Int, value: Boolean) {
        if (value) selected.add(number) else selected.remove(number)
        notifyDataSetChanged()
    }

    /** Selects or deselects every page at once. */
    fun selectAll(value: Boolean) {
        selected.clear()
        if (value) pages.forEach { selected.add(it.number) }
        notifyDataSetChanged()
    }

    /** The selected page numbers, in the order the tiles are shown in. */
    fun selectedPages(): List<Int> = pages.filter { it.number in selected }.map { it.number }

    /**
     * Moves a tile to another position.
     *
     * The design lets the user drag the tiles to change the order of the
     * pages, the split follows the new order.
     */
    fun move(from: Int, to: Int) {
        if (from !in pages.indices || to !in pages.indices) return
        pages.add(to, pages.removeAt(from))
        notifyItemMoved(from, to)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ToolsItemSplitPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return Holder(binding)
    }

    override fun getItemCount(): Int = pages.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(pages[position])
    }

    inner class Holder(private val binding: ToolsItemSplitPageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var number: Int = 0

        fun bind(page: Page) {
            number = page.number
            val context = binding.root.context
            binding.tvNumber.text = page.number.toString()
            binding.tvNumber.background = ToolShapes.badge(context, page.number in selected)
            binding.ivPage.setImageBitmap(page.thumbnail)
            binding.root.setOnClickListener { onToggle(page.number) }
        }
    }
}
