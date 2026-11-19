package com.peng.pdfreader.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.peng.pdfreader.R
import com.peng.pdfreader.core.widget.FileListItemDecoration
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.databinding.ToolsActivityCompressBinding
import com.peng.pdfreader.ui.adapter.listAdapter.DocumentPickAdapter
import com.peng.pdfreader.feature.tools.engine.ToolEngines
import com.peng.pdfreader.feature.tools.util.ToolFlow
import com.peng.pdfreader.feature.tools.util.ToolOutput
import kotlinx.coroutines.launch


/**
 * Compress flow (design "compress result page").
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

        binding.tvTitle.text = getString(R.string.compress)
        binding.ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        binding.rvDocuments.layoutManager = LinearLayoutManager(this)
        binding.rvDocuments.adapter = adapter
        binding.rvDocuments.addItemDecoration(FileListItemDecoration(dp(12)))

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
        binding.tvTitle.text = getString(R.string.compress)
        val documents = repository.all().filter { it.type == DocType.PDF }
        adapter.submit(documents)
        if (documents.isEmpty()) {
            Toast.makeText(this, R.string.tools_compress_empty, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Runs the compression behind the shared progress page.
     *
     * The compressed file takes the place of the original one - the operation
     * works on the file of the document, it does not create a copy of it.
     * The progress page opens the result page as soon as the job is done.
     */
    private fun compress(document: Document) {
        val source = repository.fileOf(document)
        val temporary = ToolOutput.file(this, "compressed.pdf")
        lifecycleScope.launch {
            ToolFlow.run(this@CompressActivity, getString(R.string.tools_compress_running)) { report ->
                val outcome = engine.compress(source, temporary, QUALITY) { current, total ->
                    report(if (total <= 0) 0 else current * 100 / total)
                }
                val result = outcome.getOrThrow()
                // The compressed file replaces the original one.
                temporary.copyTo(source, overwrite = true)
                temporary.delete()
                val updated = document.copy(
                    sizeBytes = source.length(),
                    modifiedAt = System.currentTimeMillis(),
                )
                repository.update(updated)
                ToolFlow.sizes(updated.id, result.originalSize, result.newSize)
            }
            // The job is done, the progress page takes over with the result.
            finish()
        }
    }


    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()


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
