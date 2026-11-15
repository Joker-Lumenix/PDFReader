package com.peng.pdfreader.feature.tools.activity

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.peng.pdfreader.base.ShareFileProvider
import com.peng.pdfreader.feature.lock.activity.LockActivity
import com.peng.pdfreader.feature.preview.activity.DrawActivity
import com.peng.pdfreader.feature.preview.activity.SignatureActivity
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.pdf.PdfEngine
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.data.model.DocType
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.databinding.ToolsActivityCompressBinding
import com.peng.pdfreader.feature.preview.activity.PreviewActivity
import com.peng.pdfreader.feature.tools.adapter.DocumentPickAdapter
import com.peng.pdfreader.feature.tools.dialog.ToolTextInputDialog
import com.peng.pdfreader.feature.tools.engine.ToolEngines
import com.peng.pdfreader.feature.tools.print.ToolPrintRunner
import com.peng.pdfreader.feature.tools.util.ToolOutput
import com.peng.pdfreader.feature.tools.view.ToolsResultActions
import com.peng.pdfreader.feature.tools.view.ToolsResultBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Compress flow (design "压缩结果页").
 *
 * The first step lists the documents of the sandbox, the second one shows the
 * produced document together with the size comparison.
 */
class CompressActivity : BaseActivity() {

    private lateinit var binding: ToolsActivityCompressBinding

    private val repository by lazy { DocumentRepository.get(this) }
    private val engine get() = ToolEngines.current

    private val adapter by lazy {
        DocumentPickAdapter(multiSelect = false, onClick = { document -> compress(document) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ToolsActivityCompressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.titleBar.showBack = true
        binding.titleBar.title = getString(R.string.tools_compress_title)
        binding.titleBar.onBackClick = { onBackPressedDispatcher.onBackPressed() }

        binding.rvDocuments.layoutManager = LinearLayoutManager(this)
        binding.rvDocuments.adapter = adapter

        val docId = intent.getStringExtra(EXTRA_DOC_ID)
        val document = docId?.let { repository.byId(it) }
        if (document != null) {
            compress(document)
        } else {
            showPicker()
        }
    }

    private fun showPicker() {
        binding.rvDocuments.visibility = View.VISIBLE
        binding.resultPage.root.visibility = View.GONE
        binding.titleBar.title = getString(R.string.tools_compress_title)
        val documents = repository.all().filter { it.type == DocType.PDF }
        adapter.submit(documents)
        if (documents.isEmpty()) {
            Toast.makeText(this, R.string.tools_compress_empty, Toast.LENGTH_SHORT).show()
        }
    }

    private fun compress(document: Document) {
        binding.rvDocuments.visibility = View.GONE
        binding.resultPage.root.visibility = View.VISIBLE
        binding.titleBar.title = getString(R.string.tools_compress_result_title)
        binding.titleBar.subtitle = getString(R.string.tools_compress_running)

        val out = ToolOutput.file(this, "compressed.pdf")
        lifecycleScope.launch {
            val outcome = engine.compress(repository.fileOf(document), out, QUALITY) { current, total ->
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.titleBar.subtitle = "$current/$total"
                }
            }
            outcome.fold(
                onSuccess = { result -> showResult(document, result.originalSize, result.newSize, out) },
                onFailure = { error ->
                    binding.titleBar.subtitle = null
                    Toast.makeText(
                        this@CompressActivity,
                        getString(R.string.tools_failed, error.message.orEmpty()),
                        Toast.LENGTH_SHORT,
                    ).show()
                    showPicker()
                },
            )
        }
    }

    private fun showResult(source: Document, before: Long, after: Long, file: File) {
        binding.titleBar.subtitle = null
        val document = repository.addExisting(
            ToolOutput.uniqueName(source.name.substringBeforeLast('.') + "_compressed", ".pdf"),
            DocType.PDF,
            file,
        )
        ToolsResultBinder.bindDocument(this, binding.resultPage, document)
        ToolsResultBinder.bindSizes(this, binding.resultPage, before, after)
        ToolsResultBinder.bindActions(
            binding.resultPage,
            ToolsResultActions(
                onOpenWith = { openWith(document) },
                onCompress = { compress(document) },
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
            title = getString(R.string.rename_file),
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
        }.onFailure { toastNotAvailable() }
    }
    private fun openWith(document: Document) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(ShareFileProvider.uriFor(this, repository.fileOf(document)), "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(intent) }.onFailure { toastNotAvailable() }
    }

    private fun toastNotAvailable() {
        Toast.makeText(this, R.string.tools_not_available, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val EXTRA_DOC_ID = "doc_id"

        /** JPEG quality the compressor runs with. */
        private const val QUALITY = 70

        /** Opens the compress flow, optionally for a given document. */
        fun start(context: Context, docId: String? = null) {
            val intent = Intent(context, CompressActivity::class.java)
            if (docId != null) intent.putExtra(EXTRA_DOC_ID, docId)
            context.startActivity(intent)
        }
    }
}
