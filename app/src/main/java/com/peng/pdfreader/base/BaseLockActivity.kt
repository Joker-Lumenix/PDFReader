package com.peng.pdfreader.base

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.peng.pdfreader.R
import com.peng.pdfreader.core.pdf.PdfVault
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.databinding.LockActivityFilePickerBinding
import com.peng.pdfreader.ui.adapter.listAdapter.LockFileAdapter
import com.peng.pdfreader.ui.dialogs.BottomSheetPassword
import com.peng.pdfreader.ui.widget.LockThumbnailDrawable
import com.peng.pdfreader.ui.activities.PreviewActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shared behaviour of the lock and the unlock page.
 *
 * The page shows the PDF files of the sandbox, asks for a password and reports
 * the result. The two pages only differ in the mode and in which documents
 * they accept, everything else is identical.
 */
abstract class BaseLockActivity : BaseActivity() {

    private lateinit var binding: LockActivityFilePickerBinding
    private lateinit var adapter: LockFileAdapter

    private val repository by lazy { DocumentRepository.get(this) }
    private var pending: Document? = null

    /** Lock or unlock - decides the sheet copy and the operation. */
    protected abstract val mode: BottomSheetPassword.Mode

    /** Title of the page. */
    protected abstract val titleRes: Int

    /** Documents the page offers for the operation. */
    protected abstract fun accepts(document: Document): Boolean

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
        binding.ivShare.setOnClickListener { sharePendingDocument() }

        adapter = LockFileAdapter(
            onFileClick = ::onDocumentPicked,
            onMoreClick = ::onMoreClicked,
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.emptyState.illustrationDrawable = ContextCompat.getDrawable(this, R.mipmap.bg_empty)
        binding.emptyState.title = getString(R.string.no_items)
        binding.emptyState.hint = null
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
        if (binding.resultPanel.root.visibility == View.VISIBLE) {
            binding.resultPanel.root.visibility = View.GONE
            binding.recycler.visibility = View.VISIBLE
            binding.tvTitle.text = getString(titleRes)
            binding.ivShare.visibility = View.VISIBLE
            reload()
            return true
        }
        return false
    }

    // ------------------------------------------------------------------ list

    private fun reload() {
        val documents = repository.all()
            .filter { it.type == DocType.PDF }
            .filter { accepts(it) }
        adapter.submit(documents)
        binding.recycler.visibility = if (documents.isEmpty()) View.GONE else View.VISIBLE
        binding.emptyState.visibility = if (documents.isEmpty()) View.VISIBLE else View.GONE
        binding.ivShare.visibility = if (documents.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun onDocumentPicked(document: Document) {
        pending = document
        BottomSheetPassword.newInstance(mode).show(supportFragmentManager, BottomSheetPassword.TAG)
    }

    private fun onMoreClicked(document: Document, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, MENU_SHARE, 0, R.string.share)
        popup.menu.add(0, MENU_IMPORT, 1, R.string.lock_import)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_SHARE -> {
                    shareDocument(document)
                    true
                }
                MENU_IMPORT -> {
                    importLauncher.launch(arrayOf(PDF_MIME))
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    // ------------------------------------------------------------------ operation

    private fun onPasswordEntered(password: String) {
        val document = pending ?: return
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) { runOperation(document, password) }
            outcome.fold(
                onSuccess = { file ->
                    currentSheet()?.dismiss()
                    showResult(registerResult(document, file))
                },
                onFailure = { error ->
                    currentSheet()?.setBusy(false)
                    showOperationError(error)
                },
            )
        }
    }

    /** Runs the cipher on a worker thread and returns the produced file. */
    private fun runOperation(document: Document, password: String): Result<File> {
        val source = repository.fileOf(document)
        val target = File(cacheDir, "lock-${System.currentTimeMillis()}.tmp")
        return when (mode) {
            BottomSheetPassword.Mode.LOCK -> PdfVault.lock(source, target, password)
            // The unlock page and the "enter password" sheet both decrypt.
            BottomSheetPassword.Mode.UNLOCK, BottomSheetPassword.Mode.ENTER ->
                PdfVault.unlock(source, target, password)
        }
    }

    /** Stores the produced document in the sandbox. */
    private fun registerResult(source: Document, file: File): Document {
        val name = when (mode) {
            BottomSheetPassword.Mode.LOCK -> lockedName(source.name)
            else -> unlockedName(source.name)
        }
        val stored = repository.addExisting(name, DocType.PDF, file)
        val document = stored.copy(
            isEncrypted = mode == BottomSheetPassword.Mode.LOCK,
            pageCount = source.pageCount,
        )
        repository.update(document)
        file.delete()
        return document
    }

    private fun showOperationError(error: Throwable) {
        val message = when (error) {
            is PdfVault.VaultError.WrongPassword -> getString(R.string.lock_wrong_password)
            is PdfVault.VaultError.NotEncrypted -> getString(R.string.lock_not_encrypted)
            is PdfVault.VaultError.WeakPassword ->
                error.message ?: getString(R.string.lock_operation_failed)
            else -> getString(R.string.lock_operation_failed)
        }
        val dialog = currentSheet()
        if (dialog != null && dialog.isAdded) {
            dialog.showError(message)
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------ result

    private fun showResult(document: Document) {
        binding.recycler.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        val panel = binding.resultPanel
        panel.root.visibility = View.VISIBLE
        panel.tvName.text = document.name
        panel.tvPath.text = getString(
            R.string.lock_result_path,
            File(document.path).parent ?: document.path,
        )
        panel.tvStatus.setText(
            if (mode == BottomSheetPassword.Mode.LOCK) R.string.lock_result_locked
            else R.string.lock_result_unlocked,
        )
        panel.ivThumbnail.setImageDrawable(
            LockThumbnailDrawable(
                context = this,
                base = ContextCompat.getDrawable(this, document.type.logoRes)!!,
                pageCount = document.pageCount,
                locked = mode == BottomSheetPassword.Mode.LOCK,
            ),
        )
        panel.ivThumbnail.contentDescription = document.name
        panel.btnView.setOnClickListener { PreviewActivity.start(this, document.id) }
        panel.btnShare.setOnClickListener { shareDocument(document) }
        panel.ivDocAction.setOnClickListener { openDocument(document) }
        panel.resultCard.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            // the radii need eight values - two per corner
            val top = dp(16).toFloat()
            val bottom = dp(8).toFloat()
            cornerRadii = floatArrayOf(
                top,
                top,
                top,
                top,
                bottom,
                bottom,
                bottom,
                bottom,
            )
            setColor(ContextCompat.getColor(this@BaseLockActivity, R.color.surface_card))
        }

        // The result is a page of its own, it shows the title of the design.
        binding.tvTitle.text = getString(R.string.result)
        binding.ivShare.visibility = View.GONE
        pending = document
    }

    private fun openDocument(document: Document) {
        val uri = ShareFileProvider.uriFor(this, repository.fileOf(document))
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, PDF_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, R.string.lock_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareDocument(document: Document) {
        val uri = ShareFileProvider.uriFor(this, repository.fileOf(document))
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = PDF_MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(Intent.createChooser(intent, getString(R.string.share)))
        }.onFailure {
            Toast.makeText(this, R.string.lock_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun importDocument(uri: Uri) {
        lifecycleScope.launch {
            repository.importFromUri(this@BaseLockActivity, uri)
            reload()
        }
    }

    /** Shares the document the page is about. */
    private fun sharePendingDocument() {
        val document = pending
        if (document == null) {
            Toast.makeText(this, R.string.lock_no_document, Toast.LENGTH_SHORT).show()
            return
        }
        shareDocument(document)
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
    protected fun isVaultFile(document: Document): Boolean =
        document.isEncrypted || PdfVault.isEncrypted(repository.fileOf(document))

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun lockedName(name: String): String {
        val base = name.substringBeforeLast('.', name)
        return if (base.endsWith(LOCKED_SUFFIX)) "$base.pdf" else "$base$LOCKED_SUFFIX.pdf"
    }

    private fun unlockedName(name: String): String {
        val base = name.substringBeforeLast('.', name)
        val clean = if (base.endsWith(LOCKED_SUFFIX)) base.dropLast(LOCKED_SUFFIX.length) else base
        return "$clean.pdf"
    }

    private companion object {
        const val LOCKED_SUFFIX = "_locked"
        const val PDF_MIME = "application/pdf"
        const val MENU_SHARE = 1
        const val MENU_IMPORT = 2
        const val STATE_PENDING = "lock_pending_document"
    }
}
