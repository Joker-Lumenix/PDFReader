package com.peng.pdfreader.ui.activities

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.peng.pdfreader.R
import com.peng.pdfreader.core.widget.FileListItemDecoration
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.base.ShareFileProvider
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
import kotlinx.coroutines.launch

/**
 * Merge flow (designs "merge-1", "merge-2", "merging", "Merged Successfully!").
 *
 * Step one picks the documents, step two fixes their order and the last step
 * shows the produced document.
 */
class MergeActivity : BaseActivity() {

    private lateinit var binding: ToolsActivityMergeBinding

    private val repository by lazy { DocumentRepository.get(this) }
    private val engine get() = ToolEngines.current

    private val selection = linkedSetOf<String>()
    private var documents: List<Document> = emptyList()

    private val pickAdapter by lazy {
        DocumentPickAdapter(
            multiSelect = true,
            onClick = { document -> toggle(document) },
            onCheckedChange = { document, checked ->
                if (checked) selection.add(document.id) else selection.remove(document.id)
                refreshSelectStep()
            },
            // the design of the selection step puts the selection mark on the
            // right of the row, the "more" affordance sits there as well
            selectionAtEnd = true,
            showMore = true,
        )
    }

    private val orderAdapter by lazy {
        MergeOrderAdapter { document ->
            selection.remove(document.id)
            refreshOrderStep()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ToolsActivityMergeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.ivShare.setImageResource(R.mipmap.ic_share_app)
        binding.ivShare.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.text_secondary),
        )
        binding.ivShare.setOnClickListener { shareSelection() }
        binding.ivAdd.setImageResource(R.mipmap.ic_add_circle)
        binding.ivAdd.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.text_secondary),
        )
        binding.ivAdd.setOnClickListener { showStep(Step.SELECT) }

        binding.rvFiles.layoutManager = LinearLayoutManager(this)
        binding.rvFiles.adapter = pickAdapter
        // the rows of the design are 72 high and sit 12 apart
        binding.rvFiles.addItemDecoration(FileListItemDecoration(dp(12)))
        binding.rvOrder.layoutManager = LinearLayoutManager(this)
        binding.rvOrder.adapter = orderAdapter
        binding.btnAction.setOnClickListener { onPrimaryAction() }



        loadDocuments()
        showStep(Step.SELECT)
    }

    private fun loadDocuments() {
        documents = repository.all().filter { it.type == DocType.PDF }
        pickAdapter.submit(documents)
    }

    // ------------------------------------------------------------------ steps

    private enum class Step { SELECT, ORDER }

    private fun showStep(step: Step) {
        binding.rvFiles.visibility = if (step == Step.SELECT) View.VISIBLE else View.GONE
        binding.rvOrder.visibility = if (step == Step.ORDER) View.VISIBLE else View.GONE
        binding.bottomBar.visibility = View.VISIBLE
        binding.ivShare.visibility = if (step == Step.SELECT) View.VISIBLE else View.GONE
        binding.ivAdd.visibility = if (step == Step.ORDER) View.VISIBLE else View.GONE

        when (step) {
            Step.SELECT -> refreshSelectStep()
            Step.ORDER -> refreshOrderStep()
        }
    }


    private fun refreshSelectStep() {
        binding.tvTitle.text = getString(R.string.selected_count, selection.size)
        binding.btnAction.setText(R.string.next)
        binding.btnAction.isEnabled = selection.isNotEmpty()
        binding.btnAction.alpha = if (selection.isNotEmpty()) 1f else 0.5f
        pickAdapter.submit(documents)
        selection.forEach { pickAdapter.setChecked(it, true) }
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
        pickAdapter.setChecked(document.id, selection.contains(document.id))
        refreshSelectStep()
    }

    private fun onPrimaryAction() {
        when (currentStep()) {
            Step.SELECT -> if (selection.isNotEmpty()) showStep(Step.ORDER)
            Step.ORDER -> askForName()
            else -> Unit
        }
    }

    private fun currentStep(): Step = when {
        binding.rvFiles.visibility == View.VISIBLE -> Step.SELECT
        else -> Step.ORDER
    }

    // ------------------------------------------------------------------ merge

    /**
     * Asks for the name of the document.
     *
     * The name is asked before the job starts, so the dialog of the design is
     * in the front - the progress page covers the tool while it runs.
     */
    private fun askForName() {
        BottomSheetToolTextInput.show(
            manager = supportFragmentManager,
            title = getString(R.string.save),
            initial = ToolOutput.uniqueName("merged", ".pdf"),
            onConfirmed = { name -> startMerge(name) },
        )
    }

    /**
     * Runs the merge behind the shared progress page.
     *
     * The progress page opens the result page as soon as the job is done.
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

    /** The share action of the design shares every picked document at once. */

    private fun shareSelection() {
        val files = selectedDocuments()
            .map { repository.fileOf(it) }
            .filter { it.exists() }
        if (files.isEmpty()) {
            toast(R.string.tools_result_share_unavailable)
            return
        }
        val uris = files.map { ShareFileProvider.uriFor(this, it) }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND)
                .setType("application/pdf")
                .putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .setType("application/pdf")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching {
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        }.onFailure { toast(R.string.tools_not_available) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(messageRes: Int, detail: String? = null) {
        val message = if (detail.isNullOrBlank()) {
            getString(messageRes)
        } else {
            getString(R.string.tools_failed, detail)
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun handleBackPressed(): Boolean {
        // The back key walks the flow backwards before it leaves the screen.
        return when (currentStep()) {
            Step.ORDER -> {
                showStep(Step.SELECT)
                true
            }
            else -> false
        }
    }

    companion object {
        /** Opens the merge flow. */
        fun start(context: Context) {
            context.startActivity(Intent(context, MergeActivity::class.java))
        }
    }
}

