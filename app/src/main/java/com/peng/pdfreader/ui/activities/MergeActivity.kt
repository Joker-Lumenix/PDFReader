package com.peng.pdfreader.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.R
import com.peng.pdfreader.core.widget.FileListItemDecoration
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.data.DocType

import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.databinding.ToolsActivityMergeBinding
import com.peng.pdfreader.ui.adapter.listAdapter.DocumentPickAdapter
import com.peng.pdfreader.ui.adapter.listAdapter.MergeOrderAdapter
import com.peng.pdfreader.ui.dialogs.BottomSheetToolTextInput
import com.peng.pdfreader.feature.tools.engine.ToolEngines
import com.peng.pdfreader.feature.tools.util.ToolFlow
import com.peng.pdfreader.feature.tools.util.ToolOutput
import com.peng.pdfreader.utils.ToolRowThumbnails
import kotlinx.coroutines.launch

/**
 * Merge flow (designs "merge-1", "merge-2", "merging", "Merged Successfully!").
 *
 * Step one picks the documents, step two fixes their order and the last step
 * shows the produced document.
 *
 * The search button of the design turns the selection step into its search
 * state: the field covers the title, the hits carry the same selection mark
 * as the other rows and the cancel button brings the selection step back.
 */
class MergeActivity : BaseActivity() {

    private lateinit var binding: ToolsActivityMergeBinding

    private val repository by lazy { DocumentRepository.get(this) }
    private val engine get() = ToolEngines.current

    private val selection = linkedSetOf<String>()
    private var documents: List<Document> = emptyList()

    /** The documents that the search state shows. */
    private var visible: List<Document> = emptyList()

    /** The handle that drags the rows of the ordered list. */
    private lateinit var dragHelper: ItemTouchHelper

    private val pickAdapter by lazy {
        DocumentPickAdapter(
            multiSelect = true,
            onClick = { document -> toggle(document) },
            onCheckedChange = { document, checked ->
                if (checked) selection.add(document.id) else selection.remove(document.id)
                refreshSelectStep()
            },
            // the design of the selection step puts the selection mark on the
            // right of every row
            selectionAtEnd = true,
            // the card of the design is a white surface
            designCard = true,
            // the design shows the first page of the document
            thumbnails = rowThumbnails,
        )
    }

    private val orderAdapter by lazy {
        MergeOrderAdapter(
            onRemove = { document ->
                selection.remove(document.id)
                refreshOrderStep()
            },
            onDragStart = { holder -> dragHelper.startDrag(holder) },
            thumbnails = rowThumbnails,
        )
    }

    /** The pictures of the rows, the first page of every document. */
    private val rowThumbnails by lazy {
        ToolRowThumbnails(this, repository, lifecycleScope)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ToolsActivityMergeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.ivSearch.setOnClickListener { openSearch() }
        binding.tvSearchCancel.setOnClickListener { closeSearch() }
        binding.ivAdd.setOnClickListener { showStep(Step.SELECT) }

        binding.rvFiles.layoutManager = LinearLayoutManager(this)
        binding.rvFiles.adapter = pickAdapter
        // the rows of the design are 72 high and sit 12 apart
        binding.rvFiles.addItemDecoration(FileListItemDecoration(dp(12)))
        binding.rvOrder.layoutManager = LinearLayoutManager(this)
        binding.rvOrder.adapter = orderAdapter
        binding.btnAction.setOnClickListener { onPrimaryAction() }

        binding.etSearch.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            visible = if (query.isBlank()) {
                documents
            } else {
                documents.filter { it.name.contains(query, ignoreCase = true) }
            }
            pickAdapter.submit(visible)
            pickAdapter.setCheckedIds(selection)
        }

        attachDragReorder()

        loadDocuments()
        showStep(Step.SELECT)
    }

    private fun loadDocuments() {
        documents = repository.all().filter { it.type == DocType.PDF }
        visible = documents
        pickAdapter.submit(visible)
    }

    // ------------------------------------------------------------------ steps

    private enum class Step { SELECT, ORDER }

    private fun showStep(step: Step) {
        binding.rvFiles.visibility = if (step == Step.SELECT) View.VISIBLE else View.GONE
        binding.rvOrder.visibility = if (step == Step.ORDER) View.VISIBLE else View.GONE
        binding.bottomBar.visibility = View.VISIBLE
        binding.ivSearch.visibility = if (step == Step.SELECT) View.VISIBLE else View.GONE
        binding.ivAdd.visibility = if (step == Step.ORDER) View.VISIBLE else View.GONE

        when (step) {
            Step.SELECT -> refreshSelectStep()
            Step.ORDER -> refreshOrderStep()
        }
    }

    /**
     * The search state of the design.
     *
     * The field takes the place of the title, the button of the design is
     * hidden while the search runs.
     */
    private fun openSearch() {
        binding.tvTitle.visibility = View.GONE
        binding.ivSearch.visibility = View.GONE
        binding.etSearch.visibility = View.VISIBLE
        binding.tvSearchCancel.visibility = View.VISIBLE
        binding.etSearch.requestFocus()
    }

    /** Brings the selection step back, the search is left. */
    private fun closeSearch() {
        binding.etSearch.setText("")
        binding.etSearch.clearFocus()
        // the keyboard of the search field goes away with the field
        val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE)
            as? android.view.inputmethod.InputMethodManager
        keyboard?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        binding.etSearch.visibility = View.GONE
        binding.tvSearchCancel.visibility = View.GONE
        binding.tvTitle.visibility = View.VISIBLE
        binding.ivSearch.visibility = View.VISIBLE
    }

    private fun refreshSelectStep() {
        binding.tvTitle.text = getString(R.string.selected_count, selection.size)
        binding.btnAction.setText(R.string.next)
        // the merge needs at least two documents
        val ready = selection.size >= 2
        binding.btnAction.isEnabled = ready
        binding.btnAction.alpha = if (ready) 1f else 0.5f
        pickAdapter.submit(visible)
        pickAdapter.setCheckedIds(selection)
    }

    private fun refreshOrderStep() {
        binding.tvTitle.text = getString(R.string.merge)
        binding.btnAction.text = getString(R.string.merge_count, selection.size)
        binding.btnAction.isEnabled = selection.isNotEmpty()
        binding.btnAction.alpha = if (selection.isNotEmpty()) 1f else 0.5f
        orderAdapter.submit(selectedDocuments())
    }

    private fun selectedDocuments(): List<Document> =
        selection.mapNotNull { id -> documents.firstOrNull { it.id == id } }

    private fun toggle(document: Document) {
        if (selection.contains(document.id)) selection.remove(document.id) else selection.add(document.id)
        refreshSelectStep()
    }

    private fun onPrimaryAction() {
        when (currentStep()) {
            Step.SELECT -> if (selection.size >= 2) showStep(Step.ORDER)
            Step.ORDER -> askForName()
        }
    }

    private fun currentStep(): Step = when {
        binding.rvFiles.visibility == View.VISIBLE -> Step.SELECT
        else -> Step.ORDER
    }

    // ------------------------------------------------------------------ drag

    /**
     * Lets the user drag the rows of the ordered list.
     *
     * The drag starts at the handle of the design, the order of the list is
     * the order of the merged document.
     */
    private fun attachDragReorder() {
        dragHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0,
        ) {
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
                // the selection keeps the order of the list
                selection.clear()
                selection.addAll(orderAdapter.items().map { it.id })
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        })
        dragHelper.attachToRecyclerView(binding.rvOrder)
    }

    // ------------------------------------------------------------------ merge

    /**
     * Asks for the name of the document.
     *
     * The name is asked before the job starts, so the dialog of the design is
     * in the front - the progress page covers the tool while it runs. The
     * merge always writes a PDF, so the name carries no extension.
     */
    private fun askForName() {
        BottomSheetToolTextInput.show(
            manager = supportFragmentManager,
            title = getString(R.string.save),
            initial = ToolOutput.uniqueBaseName("merged"),
            onConfirmed = { name -> startMerge(name + PDF_EXTENSION) },
        )
    }

    /**
     * Runs the merge behind the shared progress page.
     *
     * The progress page opens the result page as soon as the job is done. The
     * merge page leaves the stack with it, so the back key of the result page
     * returns to the page the flow was started from.
     */
    private fun startMerge(name: String) {
        val files = selectedDocuments().map { repository.fileOf(it) }
        if (files.isEmpty()) {
            Toast.makeText(this, R.string.tools_merge_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val out = ToolOutput.file(this, "merged.pdf")
        lifecycleScope.launch {
            ToolFlow.run(this@MergeActivity, getString(R.string.tools_merge_running)) { report ->
                val outcome = engine.merge(files, out) { current, total ->
                    report(if (total <= 0) 0 else current * 100 / total)
                }
                outcome.getOrThrow()
                val document = repository.addExisting(name, DocType.PDF, out)
                ToolFlow.success(document.id, getString(R.string.merged_success))
            }
            // The job is done, the progress page takes over with the result.
            finish()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun handleBackPressed(): Boolean {
        // The back key walks the flow backwards before it leaves the screen.
        return when {
            binding.etSearch.visibility == View.VISIBLE -> {
                closeSearch()
                true
            }

            currentStep() == Step.ORDER -> {
                showStep(Step.SELECT)
                true
            }

            else -> false
        }
    }

    companion object {
        /** The merge always writes a PDF. */
        private const val PDF_EXTENSION = ".pdf"

        /** Opens the merge flow. */
        fun start(context: Context) {
            context.startActivity(Intent(context, MergeActivity::class.java))
        }
    }
}
