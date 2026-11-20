package com.peng.pdfreader.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.peng.pdfreader.R
import com.peng.pdfreader.core.pdf.PdfEngine
import com.peng.pdfreader.ads.Ads
import com.peng.pdfreader.core.util.Logger
import com.peng.pdfreader.core.widget.AdSlotView
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.base.ShareFileProvider
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.utils.FileSharing
import com.peng.pdfreader.databinding.ActivityToolsResultBinding
import com.peng.pdfreader.ui.dialogs.BottomSheetToolTextInput
import com.peng.pdfreader.ui.uiData.AdSamples
import com.peng.pdfreader.feature.tools.print.ToolPrintRunner
import com.peng.pdfreader.feature.tools.progress.ToolJobResult
import com.peng.pdfreader.feature.tools.view.ToolsResultActions
import com.peng.pdfreader.feature.tools.view.ToolsResultBinder
import com.peng.pdfreader.ui.widget.LockThumbnailDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The result page of the tool flows.
 *
 * Every tool - image to pdf, merge, split, compress, scan - hands the document
 * it has produced over to this page. The page itself does not know which tool
 * it came from: the parameters of the intent decide what is on the screen.
 *
 * - [MODE_SUCCESS] shows the success title of the hero,
 * - [MODE_SIZES] shows the size comparison of the compress flow.
 */
class ResultActivity : BaseActivity() {

    private lateinit var binding: ActivityToolsResultBinding

    private val repository by lazy { DocumentRepository.get(this) }

    /** True when the page shows the document only - the lock flow of the design. */
    private val simple: Boolean
        get() = intent.getBooleanExtra(EXTRA_SIMPLE, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityToolsResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTitle.text = getString(R.string.result)
        binding.ivBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val document = repository.byId(intent.getStringExtra(EXTRA_DOC_ID).orEmpty())
        if (document == null) {
            Logger.w(TAG, "the result page was opened without a document")
            finish()
            return
        }

        bindMode()
        ToolsResultBinder.bindDocument(this, binding, document)
        ToolsResultBinder.bindActions(this, binding, actions(document))
        loadPreview(document)
        if (simple) hideFollowUps()

        // the ad slot of the design, between the document and the more card
        binding.adBanner.style = AdSlotView.Style.BANNER
        binding.adBanner.content = AdSamples.banner(this)
        Ads.loadBanner(binding.adBanner)
    }

    /**
     * The page of the lock flow shows the document and nothing else.
     *
     * The design of the lock has no more card and no feedback card.
     */
    private fun hideFollowUps() {
        binding.moreCard.visibility = View.GONE
        binding.feedbackCard.visibility = View.GONE
    }

    /** Shows the hero of the design - the success title or the sizes. */
    private fun bindMode() {
        when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_SIZES -> ToolsResultBinder.bindSizes(
                this,
                binding,
                intent.getLongExtra(EXTRA_SIZE_BEFORE, 0L),
                intent.getLongExtra(EXTRA_SIZE_AFTER, 0L),
            )

            else -> ToolsResultBinder.bindSuccess(
                this,
                binding,
                intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            )
        }
    }

    /**
     * The actions of the result page.
     *
     * They belong to the page, not to the tool that opened it - every tool
     * offers the same follow up actions on its result.
     */
    private fun actions(document: Document) = ToolsResultActions(
        onOpenWith = { openWith(document) },
        onCompress = { CompressActivity.start(this, document.id) },
        onPrint = { ToolPrintRunner.print(this, document) },
        onSign = { startActivity(SignatureActivity.createIntent(this)) },
        onDraw = {
            startActivity(DrawActivity.createIntent(this, repository.fileOf(document), 1, false))
        },
        onLock = { ToolFilePickerActivity.start(this, ToolFilePickerActivity.Mode.LOCK) },
        onShare = { shareDocument(document) },
        onView = { PreviewActivity.start(this, document.id) },
        onRename = { rename(document) },
        onFeedback = { binding.feedbackCard.visibility = View.GONE },
    )

    /** The preview of the produced document. */
    private fun loadPreview(document: Document) {
        // The page of an encrypted document cannot be rendered, the design
        // shows it blurred with the lock badge.
        if (document.isEncrypted) {
            binding.ivPreview.setImageDrawable(
                LockThumbnailDrawable(
                    context = this,
                    base = null,
                    pageCount = document.pageCount,
                    locked = true,
                ),
            )
            return
        }
        lifecycleScope.launch {
            val thumbnail = withContext(Dispatchers.IO) {
                PdfEngine.renderThumbnail(repository.fileOf(document), 0, 240)
            }
            binding.ivPreview.setImageBitmap(thumbnail)
        }
    }

    private fun rename(document: Document) {
        BottomSheetToolTextInput.show(
            manager = supportFragmentManager,
            title = getString(R.string.rename),
            initial = document.name,
        ) { newName ->
            repository.rename(document, newName)
            ToolsResultBinder.bindDocument(this, binding, document)
        }
    }

    /** Hands the document to another app through the file provider. */
    private fun shareDocument(document: Document) {
        FileSharing.share(
            this,
            listOf(
                FileSharing.DocumentFile(
                    repository.fileOf(document),
                    DocType.mimeTypeOf(document.name) ?: "application/pdf",
                ),
            ),
            getString(R.string.share),
        )
    }

    private fun openWith(document: Document) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(
                ShareFileProvider.uriFor(this, repository.fileOf(document)),
                "application/pdf",
            )
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { startActivity(intent) }.onFailure { toast(R.string.tools_not_available) }
    }

    private fun toast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "ResultActivity"

        /** The document the page shows. */
        const val EXTRA_DOC_ID = "com.peng.pdfreader.extra.DOC_ID"

        /** The state of the hero of the design. */
        const val EXTRA_MODE = "com.peng.pdfreader.extra.MODE"

        /** The success title of the hero. */
        const val EXTRA_TITLE = "com.peng.pdfreader.extra.TITLE"

        /** The size of the document before the tool ran. */
        const val EXTRA_SIZE_BEFORE = "com.peng.pdfreader.extra.SIZE_BEFORE"

        /** The size of the document after the tool ran. */
        const val EXTRA_SIZE_AFTER = "com.peng.pdfreader.extra.SIZE_AFTER"

        /** The page shows the document only, without the follow up cards. */
        const val EXTRA_SIMPLE = "com.peng.pdfreader.extra.SIMPLE"

        /** The hero shows the success title. */
        const val MODE_SUCCESS = "success"

        /** The hero shows the size comparison. */
        const val MODE_SIZES = "sizes"

        /** Opens the page for a tool that finished with a success. */
        fun startSuccess(context: Context, documentId: String, title: String) {
            context.startActivity(
                Intent(context, ResultActivity::class.java)
                    .putExtra(EXTRA_DOC_ID, documentId)
                    .putExtra(EXTRA_MODE, MODE_SUCCESS)
                    .putExtra(EXTRA_TITLE, title),
            )
        }

        /**
         * Opens the page for a document that a tool has changed.
         *
         * The page shows the document only - the lock flow of the design has no
         * more card and no feedback card.
         */
        fun startSimple(context: Context, documentId: String, title: String) {
            context.startActivity(
                Intent(context, ResultActivity::class.java)
                    .putExtra(EXTRA_DOC_ID, documentId)
                    .putExtra(EXTRA_MODE, MODE_SUCCESS)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_SIMPLE, true),
            )
        }

        /** Opens the page for what a job of a tool has produced. */
        fun start(context: Context, result: ToolJobResult) {
            context.startActivity(
                Intent(context, ResultActivity::class.java)
                    .putExtra(EXTRA_DOC_ID, result.documentId)
                    .putExtra(EXTRA_MODE, result.mode)
                    .putExtra(EXTRA_TITLE, result.title)
                    .putExtra(EXTRA_SIZE_BEFORE, result.sizeBefore)
                    .putExtra(EXTRA_SIZE_AFTER, result.sizeAfter),
            )
        }

        /** Opens the page for a tool that compares the size before and after. */
        fun startSizes(context: Context, documentId: String, before: Long, after: Long) {
            context.startActivity(
                Intent(context, ResultActivity::class.java)
                    .putExtra(EXTRA_DOC_ID, documentId)
                    .putExtra(EXTRA_MODE, MODE_SIZES)
                    .putExtra(EXTRA_SIZE_BEFORE, before)
                    .putExtra(EXTRA_SIZE_AFTER, after),
            )
        }
    }
}
