package com.peng.pdfreader.ui.adapter.listAdapter

import android.graphics.Bitmap
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.databinding.ToolsItemSplitPageBinding
import com.peng.pdfreader.feature.tools.util.ToolShapes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The page grid of the split screen (design "Split PDF").
 *
 * Every tile shows the rendered thumbnail of the page and the number badge in
 * its top left corner, the badge turns green as soon as the page is selected.
 *
 * The pages are rendered when they are needed, so a long document does not
 * keep a picture of every page in the memory. The pictures that were rendered
 * already stay in a small cache, so scrolling back and forth is quick.
 */
class SplitPageAdapter(
    private val scope: CoroutineScope,
    private val onToggle: (Int) -> Unit,
    private val renderer: (Int) -> Bitmap? = { null },
) : RecyclerView.Adapter<SplitPageAdapter.Holder>() {

    /** One page of the document. */
    data class Page(val number: Int)

    private val pages = mutableListOf<Page>()
    private val selected = mutableSetOf<Int>()

    /** The pictures that were rendered already. */
    private val cache = object : LruCache<Int, Bitmap>(CACHE_SIZE) {
        override fun sizeOf(key: Int, value: Bitmap): Int = 1
    }

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

    /** Every page of the list, in the order the tiles are shown in. */
    fun pages(): List<Page> = pages.toList()

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
            binding.root.setOnClickListener { onToggle(page.number) }
            showPicture(page.number)
        }

        /** Shows the picture of the page, it is rendered in the background. */
        private fun showPicture(pageNumber: Int) {
            val cached = cache.get(pageNumber)
            if (cached != null) {
                binding.ivPage.setImageBitmap(cached)
                return
            }
            binding.ivPage.setImageDrawable(null)
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) { renderer(pageNumber) } ?: return@launch
                cache.put(pageNumber, bitmap)
                // the tile may show another page already
                if (number == pageNumber) binding.ivPage.setImageBitmap(bitmap)
            }
        }
    }

    private companion object {
        /** How many pictures the cache keeps. */
        const val CACHE_SIZE = 48
    }
}
