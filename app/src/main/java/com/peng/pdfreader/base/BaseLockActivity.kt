package com.peng.pdfreader.base

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.peng.pdfreader.R
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.databinding.LockActivityFilePickerBinding
import com.peng.pdfreader.feature.lock.VaultCipher
import com.peng.pdfreader.ui.adapter.listAdapter.LockFileAdapter
import com.peng.pdfreader.ui.activities.ResultActivity
import com.peng.pdfreader.ui.dialogs.BottomSheetPassword
import com.peng.pdfreader.utils.ToolRowThumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared behaviour of the lock and the unlock page.
 *
 * The page shows the PDF files of the sandbox, asks for a password and reports
 * the result. The two pages only differ in the mode and in which documents
 * they accept, everything else is identical.
 *
 * The button in the top right corner searches in the list, the field of the
 * search takes the place of the title. The rows carry no action of their own.
 */
abstract class BaseLockActivity : BaseActivity() {

    private lateinit var binding: LockActivityFilePickerBinding
    private lateinit var adapter: LockFileAdapter

    private val repository by lazy { DocumentRepository.get(this) }
    private var pending: Document? = null

    /** The documents the page shows, the search filters them. */
    private var documents: List<Document> = emptyList()

    /** Lock or unlock - decides the sheet copy and the operation. */
    protected abstract val mode: BottomSheetPassword.Mode

    /** Title of the page. */
    protected abstract val titleRes: Int

    /** Documents the page offers for the operation. */
    protected abstract fun accepts(document: Document): Boolean

    /** The pictures of the rows, the first page of every document. */
    private val rowThumbnails by lazy {
        ToolRowThumbnails(this, repository, lifecycleScope)
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importDocument(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_PdfReader)
        super.onCreate(savedInstanceState)

        binding = LockActivityFilePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvTitle.text = getString(titleRes)
        binding.ivBack.setOnClickListener {
            if (!handleBackPressed()) finish()
        }
        binding.ivSearch.setOnClickListener { openSearch() }
        binding.tvSearchCancel.setOnClickListener { closeSearch() }

        adapter = LockFileAdapter(
            onFileClick = ::onDocumentPicked,
            thumbnails = rowThumbnails,
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.etSearch.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            val hits = if (query.isBlank()) {
                documents
            } else {
                documents.filter { it.name.contains(query, ignoreCase = true) }
            }
            adapter.submit(hits)
        }

        binding.emptyState.illustrationDrawable = ContextCompat.getDrawable(this, R.mipmap.bg_empty)
        binding.emptyState.title = getString(R.string.no_items)
        binding.emptyState.hint = null
        // The design carries no action on the rows, the import lives in the
        // empty state.
        binding.emptyState.actionText = getString(R.string.lock_import)
        binding.emptyState.onActionClick = { importLauncher.launch(arrayOf(PDF_MIME)) }
        tuneEmptyState()

        supportFragmentManager.setFragmentResultListener(
            BottomSheetPassword.RESULT_KEY,
            this,
        ) { _, bundle ->
            onPasswordEntered(bundle.getString(BottomSheetPassword.KEY_PASSWORD).orEmpty())
        }

        pending = savedInstanceState?.getString(STATE_PENDING)?.let { repository.byId(it) }
        reload()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pending?.id?.let { outState.putString(STATE_PENDING, it) }
    }

    /** The sheet that is currently on screen, if any. */
    private fun currentSheet(): BottomSheetPassword? =
        supportFragmentManager.findFragmentByTag(BottomSheetPassword.TAG) as? BottomSheetPassword

    override fun handleBackPressed(): Boolean {
        // The back key leaves the search before it leaves the page.
        if (binding.etSearch.visibility == View.VISIBLE) {
            closeSearch()
            return true
        }
        return false
    }

    // ------------------------------------------------------------------ search

    /** The search state of the design. */
    private fun openSearch() {
        binding.tvTitle.visibility = View.GONE
        binding.ivSearch.visibility = View.GONE
        binding.etSearch.visibility = View.VISIBLE
        binding.tvSearchCancel.visibility = View.VISIBLE
        binding.etSearch.requestFocus()
    }

    /** Leaves the search, the list of every document is shown again. */
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

    // ------------------------------------------------------------------ list

    private fun reload() {
        documents = repository.all()
            .filter { it.type == DocType.PDF }
            .filter { accepts(it) }
        adapter.submit(documents)
        binding.recycler.visibility = if (documents.isEmpty()) View.GONE else View.VISIBLE
        binding.emptyState.visibility = if (documents.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onDocumentPicked(document: Document) {
        pending = document
        BottomSheetPassword.newInstance(mode).show(supportFragmentManager, BottomSheetPassword.TAG)
    }

    // ------------------------------------------------------------------ operation

    private fun onPasswordEntered(password: String) {
        val document = pending ?: return
        val context = applicationContext
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                when (mode) {
                    BottomSheetPassword.Mode.LOCK -> VaultCipher.lock(context, document, password)
                    // The unlock page and the "enter password" sheet both decrypt.
                    BottomSheetPassword.Mode.UNLOCK, BottomSheetPassword.Mode.ENTER ->
                        VaultCipher.unlock(context, document, password)
                }
            }
            outcome.fold(
                onSuccess = { file ->
                    currentSheet()?.dismiss()
                    showResult(file)
                },
                onFailure = { error ->
                    currentSheet()?.setBusy(false)
                    showOperationError(error)
                },
            )
        }
    }

    private fun showOperationError(error: Throwable) {
        val message = VaultCipher.errorMessage(this, error)
        val dialog = currentSheet()
        if (dialog != null && dialog.isAdded) {
            dialog.showError(message)
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------ result

    /**
     * The result of the operation.
     *
     * The design of the lock has its own result page: the shared one without
     * the more card and without the feedback card.
     */
    private fun showResult(document: Document) {
        val title = getString(
            if (mode == BottomSheetPassword.Mode.LOCK) {
                R.string.lock_result_locked
            } else {
                R.string.lock_result_unlocked
            },
        )
        ResultActivity.startSimple(this, document.id, title)
        // The result page is the end of the flow, the picker leaves the stack.
        finish()
    }

    // ------------------------------------------------------------------ helpers

    private fun importDocument(uri: Uri) {
        lifecycleScope.launch {
            repository.importFromUri(this@BaseLockActivity, uri)
            reload()
        }
    }

    /**
     * The empty state of the design shows a 148dp illustration and a title
     * that is aligned to the left edge, the shared widget centres both.
     */
    private fun tuneEmptyState() {
        val illustration = binding.emptyState.findViewById<ImageView>(R.id.ivIllustration)
        illustration.layoutParams = illustration.layoutParams.apply {
            width = dp(148)
            height = dp(148)
        }
        val title = binding.emptyState.findViewById<TextView>(R.id.tvTitle)
        title.layoutParams = title.layoutParams.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        title.gravity = Gravity.START
    }

    /** True when the document is stored in the vault. */
    protected fun isVaultFile(document: Document): Boolean = VaultCipher.isEncrypted(this, document)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val PDF_MIME = "application/pdf"
        const val STATE_PENDING = "lock_pending_document"
    }
}
