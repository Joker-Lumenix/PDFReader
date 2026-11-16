package com.peng.pdfreader.feature.tools.activity

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.pdf.PdfEngine
import com.peng.pdfreader.appcore.widget.FileListItemDecoration
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.base.EngineErrors
import com.peng.pdfreader.base.ShareFileProvider
import com.peng.pdfreader.data.model.DocType
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.databinding.ToolsActivityMergeBinding
import com.peng.pdfreader.feature.lock.activity.LockActivity
import com.peng.pdfreader.feature.preview.activity.DrawActivity
import com.peng.pdfreader.feature.preview.activity.PreviewActivity
import com.peng.pdfreader.feature.preview.activity.SignatureActivity
import com.peng.pdfreader.feature.tools.adapter.DocumentPickAdapter
import com.peng.pdfreader.feature.tools.adapter.MergeOrderAdapter
import com.peng.pdfreader.feature.tools.dialog.ToolTextInputDialog
import com.peng.pdfreader.feature.tools.engine.ToolEngines
import com.peng.pdfreader.feature.tools.print.ToolPrintRunner
import com.peng.pdfreader.feature.tools.util.ToolOutput
import com.peng.pdfreader.feature.tools.util.ToolShapes
import com.peng.pdfreader.feature.tools.view.ToolsResultActions
import com.peng.pdfreader.feature.tools.view.ToolsResultBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    private lateinit var shareAction: ImageView
    private lateinit var addAction: ImageView

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

        binding.titleBar.showBack = true
        binding.titleBar.onBackClick = { onBackPressedDispatcher.onBackPressed() }
        shareAction = buildAction(R.mipmap.ic_share_app) { shareSelection() }
        addAction = buildAction(R.mipmap.ic_add_circle) { showStep(Step.SELECT) }
        binding.titleBar.addAction(shareAction)
        binding.titleBar.addAction(addAction)

        binding.rvFiles.layoutManager = LinearLayoutManager(this)
        binding.rvFiles.adapter = pickAdapter
        // the rows of the design are 72 high and sit 12 apart
        binding.rvFiles.addItemDecoration(FileListItemDecoration(dp(12)))
        binding.rvOrder.layoutManager = LinearLayoutManager(this)
        binding.rvOrder.adapter = orderAdapter
        binding.btnAction.setOnClickListener { onPrimaryAction() }

        // the progress bar of the design: a 240x6 track with a handle
        binding.progressTrack.background = ToolShapes.roundRect(this, TRACK_COLOR, 3f)
        binding.progressFill.background =
            ToolShapes.gradientPill(this, R.drawable.gradient_red, 16f)
        binding.progressKnob.setImageDrawable(ToolShapes.progressKnob(this))

        loadDocuments()
        showStep(Step.SELECT)
    }

    private fun loadDocuments() {
        documents = repository.all().filter { it.type == DocType.PDF }
        pickAdapter.submit(documents)
    }

    // ------------------------------------------------------------------ steps

    private enum class Step { SELECT, ORDER, PROGRESS, RESULT }

    private fun showStep(step: Step) {
        binding.rvFiles.visibility = if (step == Step.SELECT) View.VISIBLE else View.GONE
        binding.rvOrder.visibility = if (step == Step.ORDER) View.VISIBLE else View.GONE
        binding.stepProgress.visibility = if (step == Step.PROGRESS) View.VISIBLE else View.GONE
        binding.resultPage.root.visibility = if (step == Step.RESULT) View.VISIBLE else View.GONE
        binding.bottomBar.visibility =
            if (step == Step.SELECT || step == Step.ORDER) View.VISIBLE else View.GONE
        shareAction.visibility = if (step == Step.SELECT) View.VISIBLE else View.GONE
        addAction.visibility = if (step == Step.ORDER) View.VISIBLE else View.GONE

        when (step) {
            Step.SELECT -> refreshSelectStep()
            Step.ORDER -> refreshOrderStep()
            Step.PROGRESS -> {
                // the design of the running job shows an empty app bar
                binding.titleBar.title = ""
                binding.tvProgressPercent.text = "0%"
                binding.tvProgressLabel.setText(R.string.tools_merge_running)
                showProgress(0, 0)
            }
            Step.RESULT -> binding.titleBar.title = getString(R.string.result)
        }
    }

    private fun refreshSelectStep() {
        binding.titleBar.title = getString(R.string.selected_count, selection.size)
        binding.btnAction.setText(R.string.next)
        binding.btnAction.isEnabled = selection.isNotEmpty()
        binding.btnAction.alpha = if (selection.isNotEmpty()) 1f else 0.5f
        pickAdapter.submit(documents)
        selection.forEach { pickAdapter.setChecked(it, true) }
    }

    private fun refreshOrderStep() {
        binding.titleBar.title = getString(R.string.merge)
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
            Step.ORDER -> startMerge()
            else -> Unit
        }
    }

    private fun currentStep(): Step = when {
        binding.rvFiles.visibility == View.VISIBLE -> Step.SELECT
        binding.rvOrder.visibility == View.VISIBLE -> Step.ORDER
        binding.resultPage.root.visibility == View.VISIBLE -> Step.RESULT
        else -> Step.PROGRESS
    }

    // ------------------------------------------------------------------ merge

    private fun startMerge() {
        val files = selectedDocuments().map { repository.fileOf(it) }
        if (files.isEmpty()) {
            Toast.makeText(this, R.string.tools_merge_empty, Toast.LENGTH_SHORT).show()
            return
        }
        showStep(Step.PROGRESS)
        val out = ToolOutput.file(this, "merged.pdf")
        lifecycleScope.launch {
            val outcome = engine.merge(files, out) { current, total ->
                lifecycleScope.launch(Dispatchers.Main) { showProgress(current, total) }
            }
            outcome.fold(
                onSuccess = { showResult(out) },
                onFailure = { error ->
                    showStep(Step.ORDER)
                    toast(R.string.tools_failed, EngineErrors.localize(this@MergeActivity, error))
                },
            )
        }
    }

    private fun showProgress(current: Int, total: Int) {
        val percent = if (total <= 0) 0 else (current * 100 / total)
        binding.tvProgressPercent.text = "$percent%"
        val track = dp(TRACK_WIDTH_DP)
        val filled = (track * percent / 100).coerceAtLeast(if (percent > 0) dp(8) else 0)
        binding.progressFill.layoutParams = binding.progressFill.layoutParams.apply {
            width = filled
        }
        binding.progressKnob.translationX = (filled - dp(KNOB_SIZE_DP / 2)).toFloat()
    }

    private fun showResult(file: File) {
        // the design asks for the name of the document before it is stored
        showStep(Step.ORDER)
        ToolTextInputDialog.show(
            manager = supportFragmentManager,
            title = getString(R.string.save),
            initial = ToolOutput.uniqueName("merged", ".pdf"),
            onConfirmed = { name -> storeResult(file, name) },
        )
    }

    private fun storeResult(file: File, name: String) {
        val document = repository.addExisting(name, DocType.PDF, file)
        showStep(Step.RESULT)
        ToolsResultBinder.bindDocument(this, binding.resultPage, document)
        ToolsResultBinder.bindSuccess(this, binding.resultPage, getString(R.string.merged_success))
        ToolsResultBinder.bindActions(
            this,
            binding.resultPage,
            ToolsResultActions(
                onOpenWith = { openWith(document) },
                onCompress = { CompressActivity.start(this, document.id) },
                onPrint = { ToolPrintRunner.print(this, document) },
                onSign = { startActivity(SignatureActivity.createIntent(this)) },
                onDraw = { startActivity(DrawActivity.createIntent(this, repository.fileOf(document), 1, false)) },
                onLock = { LockActivity.start(this) },
                onShare = { shareDocument(document) },
                onView = { PreviewActivity.start(this, document.id) },
                onRename = { rename(document) },
                onFeedback = { binding.resultPage.feedbackCard.visibility = View.GONE },
            ),
        )
        loadPreview(document)
    }

    private fun loadPreview(document: Document) {
        lifecycleScope.launch {
            val thumbnail = withContext(Dispatchers.IO) {
                PdfEngine.renderThumbnail(repository.fileOf(document), 0, 240)
            }
            binding.resultPage.ivPreview.setImageBitmap(thumbnail)
        }
    }

    private fun rename(document: Document) {
        ToolTextInputDialog.show(
            manager = supportFragmentManager,
            title = getString(R.string.rename),
            initial = document.name,
        ) { newName ->
            repository.rename(document, newName)
            ToolsResultBinder.bindDocument(this, binding.resultPage, document)
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
        }.onFailure { toast(R.string.tools_not_available) }
    }

    private fun openWith(document: Document) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(ShareFileProvider.uriFor(this, repository.fileOf(document)), "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(intent) }.onFailure {
            toast(R.string.tools_not_available)
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

    private fun buildAction(iconRes: Int, onClick: () -> Unit): ImageView =
        ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER_VERTICAL)
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(getColor(R.color.text_secondary))
            setOnClickListener { onClick() }
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
            Step.PROGRESS, Step.RESULT -> {
                finish()
                true
            }
            else -> false
        }
    }

    companion object {
        /** Width of the progress track of the design. */
        private const val TRACK_WIDTH_DP = 240

        /** Size of the handle of the progress track. */
        private const val KNOB_SIZE_DP = 24

        /** Colour of the progress track of the design. */
        private const val TRACK_COLOR = 0xFFE1E1E1.toInt()

        /** Opens the merge flow. */
        fun start(context: Context) {
            context.startActivity(Intent(context, MergeActivity::class.java))
        }
    }
}
