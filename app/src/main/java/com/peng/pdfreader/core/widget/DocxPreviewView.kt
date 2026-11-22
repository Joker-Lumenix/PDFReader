package com.peng.pdfreader.core.widget

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.R
import com.peng.pdfreader.core.office.docx.DocxBlock
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
    private val zoom = DocumentZoom(this)

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

    /**
     * Shows the page with the given number.
     *
     * The reader uses it to go back to the page the user was on when the
     * document was read again after a change.
     */
    fun scrollToPage(page: Int) {
        val index = page.coerceIn(0, max(pageCount - 1, 0))
        recycler.scrollToPosition(index)
    }

    /**
     * Raised with the paragraph the user holds a finger on.
     *
     * The editor of the app opens the text of the paragraph, so the reader
     * only has to tell which paragraph of the document was touched.
     */
    var onParagraphLongPressed: ((DocxBlock.Paragraph) -> Unit)? = null

    /**
     * Raised when the pages of the document cannot be built.
     *
     * A document that is too big for the phone does not fit into its memory,
     * the reader tells the user that the document is not complete.
     */
    var onLayoutFailed: (() -> Unit)? = null

    /**
     * The gesture of the reader.
     *
     * The detector only watches the events, the reader keeps its own
     * handling of them.
     */
    private val longPressDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(event: MotionEvent) {
                val child = recycler.findChildViewUnder(event.x, event.y) as? DocxPageView ?: return
                val block = child.blockAt(event.x - child.left, event.y - child.top) ?: return
                val paragraph = document?.blocks?.getOrNull(block) as? DocxBlock.Paragraph ?: return
                onParagraphLongPressed?.invoke(paragraph)
            }
        },
    )

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
        // The zoomed pages may be bigger than the reader, the reader of the
        // app clips them at the border of the preview.
        clipChildren = false
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // The zoom sees every event of the gesture, the reader keeps its own.
        zoom.onTouchEvent(event)
        // The gesture that opens the editor is watched here, the reader of the
        // pages would swallow it.
        longPressDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        zoom.setContentSize(w.toFloat(), h.toFloat())
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
                try {
                    current.buildUntil(wantedPages)
                } catch (error: OutOfMemoryError) {
                    // The document does not fit into the memory of the phone,
                    // the reader shows the pages that are ready.
                    Logger.e(TAG, "the document does not fit into the memory of the phone", error)
                    withContext(Dispatchers.Main) { onLayoutFailed?.invoke() }
                    break
                } catch (error: Exception) {
                    Logger.e(TAG, "the pages of the document could not be built", error)
                    withContext(Dispatchers.Main) { onLayoutFailed?.invoke() }
                    break
                }
                withContext(Dispatchers.Main) {
                    if (engine !== current) return@withContext
                    val pages = current.pageCount
                    adapter.setCount(pages)
                    // The marker at the end of the list shows that the pages
                    // behind it are still built.
                    adapter.loading = !current.isComplete
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

    private inner class PageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        var engine: DocxLayoutEngine? = null

        /** The number of the pages that are on the screen. */
        private var count = 0

        /**
         * Whether the document is still being laid out.
         *
         * The reader shows a marker at the end of the list while the pages
         * behind it are built, so the user knows that the document is not
         * complete yet.
         */
        var loading: Boolean = false
            set(value) {
                if (field == value) return
                field = value
                notifyDataSetChanged()
            }

        override fun getItemViewType(position: Int): Int =
            if (position >= count) TYPE_FOOTER else TYPE_PAGE

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == TYPE_FOOTER) {
                return FooterHolder(
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.preview_docx_footer, parent, false),
                )
            }
            return PageHolder(DocxPageView(parent.context))
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is FooterHolder) {
                holder.view.findViewById<TextView>(R.id.tvLoading).setText(
                    R.string.preview_loading_more,
                )
                return
            }
            val current = engine
            val height = current?.pageHeight ?: 0
            val params = holder.itemView.layoutParams
            if (params != null && params.height != height) {
                params.height = height
                holder.itemView.layoutParams = params
            }
            (holder as PageHolder).view.bind(current?.page(position))
        }

        override fun getItemCount(): Int = count + if (loading) 1 else 0

        /** Sets the number of the pages that are on the screen. */
        fun setCount(value: Int) {
            if (count == value) return
            count = value
            notifyDataSetChanged()
        }
    }

    private class PageHolder(val view: DocxPageView) : RecyclerView.ViewHolder(view)

    /** The marker at the end of the list, it shows that more pages follow. */
    private class FooterHolder(val view: View) : RecyclerView.ViewHolder(view)

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
        const val PAGES_AHEAD = 4

        /** The gap between two pages in dp. */
        const val PAGE_GAP_DP = 12f

        /** The kind of the item that shows a page. */
        const val TYPE_PAGE = 0

        /** The kind of the item that shows the marker at the end. */
        const val TYPE_FOOTER = 1

        const val TAG = "DocxLayout"
    }
}
