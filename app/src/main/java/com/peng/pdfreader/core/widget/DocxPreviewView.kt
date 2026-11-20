package com.peng.pdfreader.core.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.core.office.docx.DocxDocument
import com.peng.pdfreader.core.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Renders a parsed Word document.
 *
 * The document is shown the way Word shows it: page by page, with the
 * margins of the page and the page breaks of the document.
 *
 * The pages are laid out on a background thread and only for the pages the
 * reader shows, so a big document does not block the interface. The reader
 * only ever holds the pages that are on the screen.
 */
class DocxPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val recycler = RecyclerView(context)
    private val adapter = PageAdapter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The pinch zoom of the document. */
    private val zoom = ZoomController(recycler)

    /**
     * The request to build the pages.
     *
     * The channel is conflated, so a request that comes in while the pages
     * are built is not lost.
     */
    private val requests = Channel<Unit>(Channel.CONFLATED)

    /** The number of the pages the reader asks for. */
    private var wantedPages = 1

    private var engine: DocxLayoutEngine? = null

    /** The job that builds the pages. */
    private var buildJob: kotlinx.coroutines.Job? = null

    /** The document that is rendered. */
    var document: DocxDocument? = null
        set(value) {
            field = value
            val current = value?.let { DocxLayoutEngine(context, it) }
            engine = current
            adapter.engine = current
            adapter.setCount(0)
            wantedPages = 1
            requests.trySend(Unit)
            if (current != null) build()
        }

    /** Raised with the index of the page the viewport shows. */
    var onPageChanged: ((Int) -> Unit)? = null

    /** The number of the pages the document has. */
    var pageCount: Int = 1
        private set

    /** The page the viewport shows. */
    var currentPage: Int = 0
        private set

    init {
        recycler.layoutManager = LinearLayoutManager(context)
        recycler.adapter = adapter
        recycler.setBackgroundColor(0x00000000)
        recycler.addItemDecoration(PageDecoration(resources.displayMetrics.density))
        recycler.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            val width = right - left
            if (width > 0 && width != oldRight - oldLeft) onWidthChanged(width)
        }
        recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updateCurrentPage()
                    requestPages()
                }
            },
        )
        addView(recycler, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // The pinch zoom of the document.
        recycler.setOnTouchListener { _, event ->
            zoom.onTouch(event)
            false
        }
    }

    override fun onDetachedFromWindow() {
        scope.cancel()
        super.onDetachedFromWindow()
    }

    /**
     * The pages are laid out for the width of the reader.
     *
     * The pages are dropped when the width changes, they are laid out again
     * for the new width.
     */
    private fun onWidthChanged(width: Int) {
        val current = engine ?: return
        Logger.d(TAG, "the reader is $width px wide, the page is ${current.pageWidth} px")
        current.resize(width)
        adapter.setCount(0)
        wantedPages = 1
        requests.trySend(Unit)
    }

    /**
     * Builds the pages on a background thread.
     *
     * Only the pages the reader shows and the ones behind them are built, so
     * the work stays small even for a big document.
     */
    private fun build() {
        buildJob?.cancel()
        buildJob = scope.launch {
            for (signal in requests) {
                val current = engine ?: continue
                current.buildUntil(wantedPages)
                withContext(Dispatchers.Main) {
                    if (engine !== current) return@withContext
                    val pages = current.pageCount
                    adapter.setCount(pages)
                    pageCount = max(pages, 1)
                    updateCurrentPage()
                    // The pages behind the one the reader shows are built
                    // ahead of time, so a scroll never has to wait for them.
                    if (!current.isComplete) {
                        val needed = currentPage + PAGES_AHEAD + 1
                        if (needed > wantedPages) {
                            wantedPages = needed
                            requests.trySend(Unit)
                        }
                    }
                }
            }
        }
    }

    /** Asks for the pages around the one the reader shows. */
    private fun requestPages() {
        val manager = recycler.layoutManager as? LinearLayoutManager ?: return
        val first = manager.findFirstVisibleItemPosition()
        if (first < 0) return
        val count = first + PAGES_AHEAD
        if (count > wantedPages) {
            wantedPages = count
            requests.trySend(Unit)
        }
    }

    /** Reports the page the viewport shows. */
    private fun updateCurrentPage() {
        val manager = recycler.layoutManager as? LinearLayoutManager ?: return
        val first = manager.findFirstVisibleItemPosition()
        if (first < 0) return
        val page = first.coerceIn(0, max(pageCount - 1, 0))
        if (page == currentPage) return
        currentPage = page
        onPageChanged?.invoke(page)
    }

    private inner class PageAdapter : RecyclerView.Adapter<PageHolder>() {

        var engine: DocxLayoutEngine? = null

        /** The number of the pages that are on the screen. */
        private var count = 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder =
            PageHolder(DocxPageView(parent.context))

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            val current = engine
            val height = current?.pageHeight ?: 0
            val params = holder.view.layoutParams
            if (params != null && params.height != height) {
                params.height = height
                holder.view.layoutParams = params
            }
            holder.view.bind(current?.page(position))
        }

        override fun getItemCount(): Int = count

        /** Sets the number of the pages that are on the screen. */
        fun setCount(value: Int) {
            if (count == value) return
            count = value
            notifyDataSetChanged()
        }
    }

    private class PageHolder(val view: DocxPageView) : RecyclerView.ViewHolder(view)

    /** The gap between two pages of the document. */
    private class PageDecoration(private val density: Float) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            val gap = (PAGE_GAP_DP * density).toInt()
            outRect.bottom = gap
        }
    }

    private companion object {
        /** The number of the pages that are built behind the visible one. */
        const val PAGES_AHEAD = 2

        /** The gap between two pages in dp. */
        const val PAGE_GAP_DP = 12f

        const val TAG = "DocxLayout"
    }
}
