package com.peng.pdfreader.ui.activities

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.R
import com.peng.pdfreader.core.pdf.PdfEngine
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.databinding.ToolsActivitySplitBinding
import com.peng.pdfreader.ui.adapter.decoration.UniformGridDecoration
import com.peng.pdfreader.ui.adapter.listAdapter.SplitPageAdapter
import com.peng.pdfreader.ui.dialogs.BottomSheetToolDocumentPicker
import com.peng.pdfreader.ui.dialogs.BottomSheetToolTextInput
import com.peng.pdfreader.feature.tools.engine.ToolEngines
import com.peng.pdfreader.feature.tools.util.ToolFlow
import com.peng.pdfreader.feature.tools.util.ToolOutput
import com.peng.pdfreader.feature.tools.util.ToolShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Split flow (designs "Split PDF", "Split PDF2", "Split PDF-rename").
 *
 * The screen shows the pages of the selected document, the user marks the
 * pages that belong into the new document and drags them into the order they
 * should have. The name of the new document is asked before the job starts.
 */
class SplitActivity : BaseActivity() {

    private lateinit var binding: ToolsActivitySplitBinding

    private val repository by lazy { DocumentRepository.get(this) }
    private val engine get() = ToolEngines.current

    private var document: Document? = null
    private var pageCount = 0

    /** The adapter of the first step, it shows every page of the document. */
    private val adapter by lazy {
        SplitPageAdapter(lifecycleScope, { page -> togglePage(page) }, ::renderPage)
    }

    /** The adapter of the second step, it shows the marked pages only. */
    private val orderAdapter by lazy {
        SplitPageAdapter(
            scope = lifecycleScope,
            onToggle = { },
            renderer = ::renderPage,
            // The tiles of the second step carry no action of their own, the
            // drag starts as soon as the finger touches them.
            onDragStart = { holder -> orderHelper.startDrag(holder) },
        )
    }

    /** The file of the document, the pages are rendered out of it. */
    private var source: File? = null

    /** The handle that drags the tiles of the second step. */
    private lateinit var orderHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ToolsActivitySplitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTitle.text = getString(R.string.split)
        binding.ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.banner.background = ToolShapes.roundRect(this, ToolShapes.BANNER_BLUE, 8f)

        binding.banner.setOnClickListener { openRangeDialog() }

        // the three tiles of a row share the width of the screen, the gap
        // between them and at the border is 12
        val decoration = UniformGridDecoration(gapPx = dp(12))
        binding.rvPages.layoutManager = GridLayoutManager(this, 3)
        binding.rvPages.adapter = adapter
        binding.rvPages.addItemDecoration(decoration)
        binding.rvOrder.layoutManager = GridLayoutManager(this, 3)
        binding.rvOrder.adapter = orderAdapter
        binding.rvOrder.addItemDecoration(decoration)
        attachOrderDrag()

        binding.btnSplit.setOnClickListener { onSplitAction() }

        binding.emptyState.title = getString(R.string.tools_split_empty)
        binding.emptyState.hint = getString(R.string.tools_empty_documents)
        binding.emptyState.actionText = getString(R.string.tools_pick_document)
        binding.emptyState.onActionClick = { pickDocument() }

        val docId = intent.getStringExtra(EXTRA_DOC_ID)
        if (docId == null) {
            showEmpty()
        } else {
            document = repository.byId(docId)
            if (document == null) showEmpty() else loadPages()
        }
    }

    // ------------------------------------------------------------------ states

    private fun showEmpty() {
        binding.stepPages.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
        binding.bottomBar.visibility = View.GONE
    }

    private fun showPages() {
        binding.stepPages.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        binding.bottomBar.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------------ pages

    private fun loadPages() {
        val doc = document ?: return
        showPages()
        showStep(Step.SELECT)
        lifecycleScope.launch {
            val file = repository.fileOf(doc)
            source = file
            val total = withContext(Dispatchers.IO) { PdfEngine.pageCount(file) }
            pageCount = total
            if (total <= 0) {
                Toast.makeText(this@SplitActivity, R.string.tools_split_empty, Toast.LENGTH_SHORT)
                    .show()
                showEmpty()
                return@launch
            }
            adapter.submit((0 until minOf(total, MAX_RENDERED_PAGES)).map { SplitPageAdapter.Page(it + 1) })
            refreshBanner()
        }
    }

    /**
     * Renders the picture of a page.
     *
     * The page is rendered with the size of a tile of the grid, so the picture
     * is as sharp as the tile is big.
     */
    private fun renderPage(number: Int): Bitmap? {
        val file = source ?: return null
        return PdfEngine.renderThumbnail(file, number - 1, tileWidthPx())
    }

    /**
     * The width of a tile of the grid, in pixels.
     */
    private fun tileWidthPx(): Int {
        val gap = dp(12)
        val width = resources.displayMetrics.widthPixels
        return ((width - gap * 4) / 3).coerceAtLeast(dp(64))
    }

    private fun togglePage(page: Int) {
        val isSelected = page in adapter.selectedPages()
        adapter.setSelected(page, !isSelected)
        refreshBanner()
    }

    // ------------------------------------------------------------------ steps

    private enum class Step { SELECT, ORDER }

    /** The two steps of the flow, the design calls them "Split PDF" and "Split PDF2". */
    private fun showStep(step: Step) {
        binding.rvPages.visibility = if (step == Step.SELECT) View.VISIBLE else View.GONE
        binding.rvOrder.visibility = if (step == Step.ORDER) View.VISIBLE else View.GONE
        when (step) {
            Step.SELECT -> refreshBanner()
            Step.ORDER -> {
                orderAdapter.submit(adapter.selectedPages().map { SplitPageAdapter.Page(it) })
                orderAdapter.selectAll(true)
                binding.bannerText.text = getString(R.string.tools_split_drag_hint)
            }
        }
    }

    /** The banner carries the state of the screen. */
    private fun refreshBanner() {
        binding.bannerText.text = if (currentStep() == Step.ORDER) {
            getString(R.string.tools_split_drag_hint)
        } else {
            getString(R.string.tools_split_select_hint)
        }
    }

    /**
     * Lets the user drag the tiles of the second step to change the order of
     * the result, the design shows the drag hint in the banner while a tile is
     * dragged.
     */
    private fun attachOrderDrag() {
        orderHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or
                ItemTouchHelper.RIGHT,
            0,
        ) {
            // The drag starts at the touch on the tile, the long press is not
            // needed - the tiles of this step carry no action of their own.
            override fun isLongPressDragEnabled(): Boolean = false

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                orderAdapter.move(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                refreshBanner()
            }
        })
        orderHelper.attachToRecyclerView(binding.rvOrder)
    }

    private fun openRangeDialog() {
        if (pageCount <= 0) return
        BottomSheetToolTextInput.show(
            manager = supportFragmentManager,
            title = getString(R.string.tools_split_range),
            initial = adapter.selectedPages().joinToString(",") { it.toString() },
        ) { expression ->
            val pages = engine.parsePageNumbers(expression, pageCount)
            if (pages.isEmpty()) {
                Toast.makeText(this, R.string.tools_split_range_invalid, Toast.LENGTH_SHORT).show()
                return@show
            }
            adapter.selectAll(false)
            pages.forEach { adapter.setSelected(it, true) }
            refreshBanner()
        }
    }

    // ------------------------------------------------------------------ split

    /**
     * The button of the design.
     *
     * The first step goes to the second one, there the user drags the marked
     * pages into the order of the new document. The second step asks for the
     * name and builds the document.
     */
    private fun onSplitAction() {
        when (currentStep()) {
            Step.SELECT -> {
                if (adapter.selectedPages().isEmpty()) {
                    Toast.makeText(this, R.string.select_page_hint, Toast.LENGTH_SHORT).show()
                    return
                }
                showStep(Step.ORDER)
            }

            Step.ORDER -> askForName()
        }
    }

    private fun currentStep(): Step =
        if (binding.rvOrder.visibility == View.VISIBLE) Step.ORDER else Step.SELECT

    /** Asks for the name of the new document (design "Split PDF-rename"). */
    private fun askForName() {
        val pages = orderAdapter.selectedPages()
        if (pages.isEmpty()) {
            Toast.makeText(this, R.string.select_page_hint, Toast.LENGTH_SHORT).show()
            return
        }
        BottomSheetToolTextInput.show(
            manager = supportFragmentManager,
            title = getString(R.string.rename),
            // the split always writes a PDF, so the name carries no extension
            initial = ToolOutput.uniqueBaseName("split"),
            onConfirmed = { name -> startSplit(name + PDF_EXTENSION, pages) },
        )
    }

    /**
     * Runs the split behind the shared progress page.
     *
     * The pages of the second step are the order of the new document, the
     * progress page takes over with the result. The split page leaves the
     * stack with it, so the back key of the result page returns to the page
     * the flow was started from.
     */
    private fun startSplit(name: String, pages: List<Int>) {
        val doc = document ?: return
        val out = ToolOutput.file(this, "split.pdf")
        lifecycleScope.launch {
            ToolFlow.run(this@SplitActivity, getString(R.string.tools_split_running)) { report ->
                val outcome = engine.extractPages(repository.fileOf(doc), out, pages) { current, total ->
                    report(if (total <= 0) 0 else current * 100 / total)
                }
                outcome.getOrThrow()
                val stored = repository.addExisting(name, DocType.PDF, out)
                ToolFlow.success(stored.id, getString(R.string.tools_split_done, 1))
            }
            // The job is done, the progress page takes over with the result.
            finish()
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun pickDocument() {
        BottomSheetToolDocumentPicker.show(supportFragmentManager) { picked ->
            document = picked
            loadPages()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun handleBackPressed(): Boolean {
        // The back key walks the flow backwards before it leaves the screen.
        return if (currentStep() == Step.ORDER) {
            showStep(Step.SELECT)
            true
        } else {
            false
        }
    }

    companion object {
        private const val EXTRA_DOC_ID = "doc_id"
        private const val MAX_RENDERED_PAGES = 120

        /** The split always writes a PDF. */
        private const val PDF_EXTENSION = ".pdf"

        /** Opens the split flow for the given document. */
        fun start(context: Context, docId: String? = null) {
            val intent = Intent(context, SplitActivity::class.java)
            if (docId != null) intent.putExtra(EXTRA_DOC_ID, docId)
            context.startActivity(intent)
        }
    }
}
