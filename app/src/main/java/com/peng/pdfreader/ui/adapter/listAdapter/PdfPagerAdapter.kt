package com.peng.pdfreader.ui.adapter.listAdapter

import android.graphics.RectF
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.appcore.pdf.PdfEngine
import com.peng.pdfreader.appcore.widget.PdfMark
import com.peng.pdfreader.appcore.widget.PdfPageView
import com.peng.pdfreader.appcore.widget.ReadingMode
import java.io.File

/**
 * The pages of the reader.
 *
 * Every page is a [PdfPageView] that renders its own bitmap; the adapter
 * keeps the created views so the marks of a page can be refreshed without
 * rendering the page again.
 */
class PdfPagerAdapter(
    private val file: File,
    private val pageCount: Int,
    private val readingMode: ReadingMode,
    private val image: Boolean = false,
) : RecyclerView.Adapter<PdfPagerAdapter.Holder>() {

    private val views = HashMap<Int, PdfPageView>()

    /** Supplies the marks of a page. */
    var marksProvider: ((Int) -> List<PdfMark>)? = null

    /** Raised when a page could not be rendered. */
    var onRenderFailed: (() -> Unit)? = null

    private var selectionEnabled = false
    private var selectionListener: ((Int, RectF) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = PdfPageView(parent.context)
        // ViewPager2 only accepts pages that fill it completely.
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val view = holder.view
        views[position] = view
        view.setReadingMode(readingMode)
        view.setMarks(marksProvider?.invoke(position).orEmpty())
        view.onRenderFailed = { onRenderFailed?.invoke() }
        applySelectionTo(view, position)
        if (image) {
            view.setImage(file)
        } else {
            view.setPage(file, position)
        }
    }

    override fun onViewRecycled(holder: Holder) {
        val position = holder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) views.remove(position)
        // The bitmap of the page is the biggest object of the reader, it is
        // given back as soon as the view leaves the screen.
        holder.view.release()
        super.onViewRecycled(holder)
    }

    override fun getItemCount(): Int = pageCount

    /** Repaints the marks of one page. */
    fun refreshMarks(position: Int) {
        views[position]?.setMarks(marksProvider?.invoke(position).orEmpty())
    }

    /** The view of a page, when it is alive. */
    fun pageView(position: Int): PdfPageView? = views[position]

    /** The page that is currently on screen. */
    fun currentView(): PdfPageView? = views.values.firstOrNull { it.isShown }

    /**
     * Switches the selection mode of every page.
     *
     * @param enabled whether the user may drag out an area
     * @param listener raised with the page and the selected area
     */
    fun applySelection(enabled: Boolean, listener: ((Int, RectF) -> Unit)?) {
        selectionEnabled = enabled
        selectionListener = listener
        views.forEach { (position, view) -> applySelectionTo(view, position) }
    }

    private fun applySelectionTo(view: PdfPageView, position: Int) {
        view.isSelectionEnabled = selectionEnabled
        val listener = selectionListener
        view.onSelection = if (listener == null) {
            null
        } else {
            { rect: RectF -> listener(position, rect) }
        }
    }

    /** Switches the reading mode of every page that is alive. */
    fun applyReadingMode(mode: ReadingMode) {
        views.values.forEach { it.setReadingMode(mode) }
    }

    /**
     * Gives the memory of the document back.
     *
     * The engine keeps the parsed document open while the reader works on it,
     * this closes it again.
     */
    fun release() {
        views.values.forEach { it.release() }
        views.clear()
        PdfEngine.release(file)
    }

    class Holder(val view: PdfPageView) : RecyclerView.ViewHolder(view)
}
