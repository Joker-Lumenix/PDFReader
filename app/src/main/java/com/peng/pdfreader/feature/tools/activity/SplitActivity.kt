package com.peng.pdfreader.feature.tools.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.pdf.PdfEngine
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.base.ShareFileProvider
import com.peng.pdfreader.data.model.DocType
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.databinding.ToolsActivitySplitBinding
import com.peng.pdfreader.feature.lock.activity.LockActivity
import com.peng.pdfreader.feature.preview.activity.DrawActivity
import com.peng.pdfreader.feature.preview.activity.PreviewActivity
import com.peng.pdfreader.feature.preview.activity.SignatureActivity
import com.peng.pdfreader.feature.tools.adapter.PitchGridDecoration
import com.peng.pdfreader.feature.tools.adapter.SplitPageAdapter
import com.peng.pdfreader.feature.tools.dialog.ToolDocumentPickerDialog
import com.peng.pdfreader.feature.tools.dialog.ToolTextInputDialog
import com.peng.pdfreader.feature.tools.engine.ToolEngines
import com.peng.pdfreader.feature.tools.print.ToolPrintRunner
import com.peng.pdfreader.feature.tools.util.ToolOutput
import com.peng.pdfreader.feature.tools.util.ToolShapes
import com.peng.pdfreader.feature.tools.view.InfoGlyphDrawable
import com.peng.pdfreader.feature.tools.view.ToolsResultActions
import com.peng.pdfreader.feature.tools.view.ToolsResultBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Split flow (designs "Split PDF", "Split PDF2", "Split PDF-rename").
 *
 * The screen shows the pages of the selected document, the user marks the
 * pages that have to become their own document and may drag the tiles to
 * change the order of the result.
 */
class SplitActivity : BaseActivity() {

    private lateinit var binding: ToolsActivitySplitBinding

    private val repository by lazy { DocumentRepository.get(this) }
    private val engine get() = ToolEngines.current

    private var document: Document? = null
    private var pageCount = 0
    private var dragging = false

    private val adapter by lazy {
        SplitPageAdapter { page -> togglePage(page) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ToolsActivitySplitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.titleBar.showBack = true
        binding.titleBar.title = getString(R.string.split)
        binding.titleBar.onBackClick = { onBackPressedDispatcher.onBackPressed() }

        binding.banner.background = ToolShapes.roundRect(this, ToolShapes.BANNER_BLUE, 8f)
        binding.bannerIcon.setImageDrawable(InfoGlyphDrawable(dp(2).toFloat()))
        binding.banner.setOnClickListener { openRangeDialog() }

        binding.rvPages.layoutManager = GridLayoutManager(this, 3)
        binding.rvPages.adapter = adapter
        // the tiles of the design sit at x = 12 / 128 / 244, 12 apart
        binding.rvPages.addItemDecoration(
            PitchGridDecoration(
                cellWidthPx = dp(104),
                pitchPx = dp(116),
                startMarginPx = dp(12),
                rowSpacingPx = dp(12),
            ),
        )
        attachDragReorder()

        binding.btnSplit.setOnClickListener { startSplit() }

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
        binding.resultPage.root.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE
        binding.bottomBar.visibility = View.GONE
    }

    private fun showPages() {
        binding.stepPages.visibility = View.VISIBLE
        binding.resultPage.root.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        binding.bottomBar.visibility = View.VISIBLE
    }

    private fun showResult() {
        binding.stepPages.visibility = View.GONE
        binding.resultPage.root.visibility = View.VISIBLE
        binding.emptyState.visibility = View.GONE
        binding.bottomBar.visibility = View.GONE
    }

    // ------------------------------------------------------------------ pages

    private fun loadPages() {
        val doc = document ?: return
        showPages()
        lifecycleScope.launch {
            val file = repository.fileOf(doc)
            val total = withContext(Dispatchers.IO) { PdfEngine.pageCount(file) }
            pageCount = total
            if (total <= 0) {
                Toast.makeText(this@SplitActivity, R.string.tools_split_empty, Toast.LENGTH_SHORT)
                    .show()
                showEmpty()
                return@launch
            }
            val pages = withContext(Dispatchers.IO) {
                (0 until minOf(total, MAX_RENDERED_PAGES)).map { index ->
                    SplitPageAdapter.Page(index + 1, PdfEngine.renderThumbnail(file, index, 240))
                }
            }
            adapter.submit(pages)
            refreshBanner()
        }
    }

    private fun togglePage(page: Int) {
        val isSelected = page in adapter.selectedPages()
        adapter.setSelected(page, !isSelected)
        refreshBanner()
    }

    /** The banner carries the state of the screen: the drag hint while a tile is dragged. */
    private fun refreshBanner() {
        binding.bannerText.text = if (dragging) {
            getString(R.string.tools_split_banner_drag)
        } else {
            getString(R.string.select_page_hint)
        }
    }

    /**
     * Lets the user drag the tiles to change the order of the result, the
     * design shows the drag hint in the banner while a tile is dragged.
     */
    private fun attachDragReorder() {
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or
                ItemTouchHelper.RIGHT,
            0,
        ) {
            override fun isLongPressDragEnabled(): Boolean = true

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                adapter.move(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                dragging = actionState == ItemTouchHelper.ACTION_STATE_DRAG
                refreshBanner()
            }
        })
        helper.attachToRecyclerView(binding.rvPages)
    }

    private fun openRangeDialog() {
        if (pageCount <= 0) return
        ToolTextInputDialog.show(
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

    private fun startSplit() {
        val doc = document ?: return
        val selected = adapter.selectedPages()
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.select_page_hint, Toast.LENGTH_SHORT).show()
            return
        }
        val ranges = rangesOf(selected)
        val outDir = ToolOutput.dir(this)
        lifecycleScope.launch {
            val outcome = engine.split(repository.fileOf(doc), outDir, ranges) { current, total ->
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.titleBar.subtitle = "$current/$total"
                }
            }
            outcome.fold(
                onSuccess = { files -> showResults(files) },
                onFailure = { error ->
                    Toast.makeText(
                        this@SplitActivity,
                        getString(R.string.tools_failed, error.message.orEmpty()),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }

    private fun showResults(files: List<File>) {
        if (files.isEmpty()) {
            Toast.makeText(this, R.string.tools_split_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val stored = files.map { file ->
            repository.addExisting(file.name, DocType.PDF, file)
        }
        Toast.makeText(
            this,
            getString(R.string.tools_split_done, stored.size),
            Toast.LENGTH_SHORT,
        ).show()
        val first = stored.first()
        showResult()
        ToolsResultBinder.bindDocument(this, binding.resultPage, first)
        ToolsResultBinder.bindSuccess(this, binding.resultPage, getString(R.string.tools_split_done, stored.size))
        ToolsResultBinder.bindActions(
            this,
            binding.resultPage,
            ToolsResultActions(
                onOpenWith = { openWith(first) },
                onCompress = { CompressActivity.start(this, first.id) },
                onPrint = { ToolPrintRunner.print(this, first) },
                onSign = { startActivity(SignatureActivity.createIntent(this)) },
                onDraw = { startActivity(DrawActivity.createIntent(this, repository.fileOf(first), 1, false)) },
                onLock = { LockActivity.start(this) },
                onShare = { shareDocument(first) },
                onView = { PreviewActivity.start(this, first.id) },
                onRename = { rename(first) },
                onFeedback = { binding.resultPage.feedbackCard.visibility = View.GONE },
            ),
        )
    }

    private fun rangesOf(pages: List<Int>): List<IntRange> {
        if (pages.isEmpty()) return emptyList()
        val ranges = mutableListOf<IntRange>()
        var start = pages.first()
        var previous = start
        pages.drop(1).forEach { page ->
            if (page == previous + 1) {
                previous = page
            } else {
                ranges += start..previous
                start = page
                previous = page
            }
        }
        ranges += start..previous
        return ranges
    }

    // ------------------------------------------------------------------ helpers

    private fun pickDocument() {
        ToolDocumentPickerDialog.show(supportFragmentManager) { picked ->
            document = picked
            loadPages()
        }
    }

    private fun rename(doc: Document) {
        ToolTextInputDialog.show(
            manager = supportFragmentManager,
            title = getString(R.string.rename),
            initial = doc.name,
        ) { newName ->
            repository.rename(doc, newName)
            ToolsResultBinder.bindDocument(this, binding.resultPage, doc)
        }
    }

    /** Hands the result to another app through the file provider. */
    private fun shareDocument(document: Document) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, ShareFileProvider.uriFor(this, repository.fileOf(document)))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        }.onFailure { toastNotAvailable() }
    }

    private fun openWith(doc: Document) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(ShareFileProvider.uriFor(this, repository.fileOf(doc)), "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(intent) }.onFailure { toastNotAvailable() }
    }

    private fun toastNotAvailable() {
        Toast.makeText(this, R.string.tools_not_available, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun handleBackPressed(): Boolean {
        if (binding.resultPage.root.visibility == View.VISIBLE) {
            showPages()
            return true
        }
        return false
    }

    companion object {
        private const val EXTRA_DOC_ID = "doc_id"
        private const val MAX_RENDERED_PAGES = 120

        /** Opens the split flow for the given document. */
        fun start(context: Context, docId: String? = null) {
            val intent = Intent(context, SplitActivity::class.java)
            if (docId != null) intent.putExtra(EXTRA_DOC_ID, docId)
            context.startActivity(intent)
        }
    }
}
