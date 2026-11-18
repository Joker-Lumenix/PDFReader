package com.peng.pdfreader.ui.activities

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.peng.pdfreader.R
import com.peng.pdfreader.base.ShareFileProvider
import com.peng.pdfreader.core.util.Logger
import com.peng.pdfreader.core.util.SystemBarInsets
import com.peng.pdfreader.core.widget.FileItemUiState
import com.peng.pdfreader.core.widget.FileListItemDecoration
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.AppPrefs
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.databinding.ActivityFileSearchBinding
import com.peng.pdfreader.ui.adapter.listAdapter.DocumentPrintAdapter
import com.peng.pdfreader.ui.adapter.listAdapter.FileListAdapter
import com.peng.pdfreader.ui.dialogs.BottomSheetRename
import com.peng.pdfreader.ui.dialogs.DialogDeleteConfirm
import com.peng.pdfreader.ui.dialogs.DocumentActionSheet
import com.peng.pdfreader.ui.dialogs.DocumentCipher
import com.peng.pdfreader.ui.uiData.FileActions
import com.peng.pdfreader.utils.DocumentThumbnails
import com.peng.pdfreader.utils.FileUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 文件搜索页面
 */
class FileSearchActivity : BaseActivity() {

    private lateinit var binding: ActivityFileSearchBinding
    private lateinit var repository: DocumentRepository
    private lateinit var adapter: FileListAdapter
    private lateinit var thumbnails: DocumentThumbnails

    /** The keyword of the last query. */
    private var keyword: String? = null

    /** True while the more sheet of a row is open. */
    private var actionSheetOpen = false

    /** True when an operation of the more sheet was picked. */
    private var actionPicked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = DocumentRepository.get(this)
        thumbnails = DocumentThumbnails(repository)

        setupTitleBar()
        setupSearch()
        setupList()
        setupEmptyState()
        registerResults()
        watchActionSheets()

        binding.etSearch.requestFocus()
        // The page starts with all the files, the keyword narrows them down.
        load()
    }

    // ------------------------------------------------------------------ setup

    private fun setupTitleBar() {
        binding.tvTitle.text = ""
        binding.ivBack.setOnClickListener { finish() }
    }

    private fun setupSearch() {
        // The search field covers the title strip, but it has to stay below the
        // status bar, so the overlay is pushed down by the status bar inset.
        binding.searchBar.setOnApplyWindowInsetsListener { view, insets ->
            val top = SystemBarInsets.top(insets)
            val params = view.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null && params.topMargin != top) {
                params.topMargin = top
                view.layoutParams = params
            }
            insets
        }
        binding.searchBar.requestApplyInsets()
        binding.etSearch.doAfterTextChanged { text ->
            val value = text?.toString().orEmpty().trim()
            if (value == keyword.orEmpty()) return@doAfterTextChanged
            keyword = value.ifEmpty { null }
            load()
        }
        binding.tvSearchCancel.setOnClickListener { finish() }
    }

    private fun setupList() {
        adapter = FileListAdapter(
            object : FileListAdapter.Callbacks {
                override fun onRowClick(document: Document) {
                    // The keyboard leaves first, the page must not jump under
                    // the finger that picked the row.
                    hideKeyboard()
                    PreviewActivity.start(this@FileSearchActivity, document.id)
                }

                override fun onRowMore(document: Document) {
                    // The keyboard leaves first, the sheet opens on top of it.
                    hideKeyboard()
                    actionSheetOpen = true
                    actionPicked = false
                    DocumentActionSheet.show(
                        this@FileSearchActivity,
                        supportFragmentManager,
                        document,
                    )
                }

                override fun onRowChecked(document: Document, checked: Boolean) = Unit

                override fun onRowLongPressed(document: Document) = Unit
            },
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.recycler.setHasFixedSize(true)
        binding.recycler.addItemDecoration(FileListItemDecoration(dp(ROW_GAP)))
    }

    /**
     * Takes the focus from the search field and hides the keyboard.
     */
    private fun hideKeyboard() {
        binding.etSearch.clearFocus()
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    /**
     * Gives the focus back to the search field and shows the keyboard.
     */
    private fun focusSearch() {
        binding.etSearch.requestFocus()
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    /**
     * The more sheet of a row is watched: when it goes away without an
     * operation, the search field takes the focus back and the keyboard comes.
     */
    private fun watchActionSheets() {
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentDestroyed(fm: FragmentManager, fragment: Fragment) {
                    if (!actionSheetOpen) return
                    actionSheetOpen = false
                    // A picked operation brings its own sheet, the search field
                    // must not take the focus away from it.
                    if (actionPicked) return
                    // The window of the sheet is still on its way out, the
                    // focus comes back when it is gone.
                    binding.root.postDelayed({ focusSearch() }, FOCUS_BACK_DELAY_MS)
                }
            },
            false,
        )
    }

    private fun setupEmptyState() {
        binding.emptyState.illustrationDrawable =
            ContextCompat.getDrawable(this, R.mipmap.bg_empty)
        binding.emptyState.title = getString(R.string.files_search_empty_title)
        binding.emptyState.hint = getString(R.string.files_search_empty_hint)
    }

    /** The more sheet of a row reports its operation through this channel. */
    private fun registerResults() {
        supportFragmentManager.setFragmentResultListener(
            FileActions.REQUEST_KEY,
            this,
        ) { _, bundle ->
            onFileAction(
                bundle.getString(FileActions.KEY_ACTION),
                bundle.getString(FileActions.KEY_DOC_ID),
            )
        }
        supportFragmentManager.setFragmentResultListener(
            BottomSheetRename.REQUEST_KEY,
            this,
        ) { _, _ -> load() }
        supportFragmentManager.setFragmentResultListener(
            DialogDeleteConfirm.REQUEST_KEY,
            this,
        ) { _, _ -> load() }
    }

    /** Runs the operation that was picked in the more sheet of a row. */
    private fun onFileAction(action: String?, docId: String?) {
        if (action == null || docId == null) return
        // The operation brings its own sheet, the search field must not take
        // the focus away from it.
        actionPicked = true
        when (action) {
            FileActions.RENAME -> BottomSheetRename.show(supportFragmentManager, docId)
            FileActions.DELETE -> DialogDeleteConfirm.show(supportFragmentManager, listOf(docId))
            FileActions.OPEN_WITH -> openWith(docId)
            FileActions.SHARE -> share(docId)
            FileActions.PRINT -> printDocument(docId)
            FileActions.SPLIT -> SplitActivity.start(this, docId)
            FileActions.COMPRESS -> CompressActivity.start(this, docId)
            FileActions.MERGE -> MergeActivity.start(this)
            // The sheet knows its file, so the cipher runs on it right away.
            FileActions.LOCK -> DocumentCipher(this, supportFragmentManager, this).encrypt(docId)
            FileActions.UNLOCK -> DocumentCipher(this, supportFragmentManager, this).decrypt(docId)
            FileActions.CHANGED -> load()
            else -> toast(getString(R.string.files_feature_unavailable))
        }
    }

    /** Hands the document to another application. */
    private fun openWith(docId: String) {
        val document = repository.byId(docId) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri(document), mimeType(document))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
            .onFailure { toast(getString(R.string.files_feature_unavailable)) }
    }

    /** Shares the document with another application. */
    private fun share(docId: String) {
        val document = repository.byId(docId) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType(document)
            putExtra(Intent.EXTRA_STREAM, contentUri(document))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, getString(R.string.files_share))) }
            .onFailure { toast(getString(R.string.files_feature_unavailable)) }
    }

    /** Sends the document to the printer of the system. */
    private fun printDocument(docId: String) {
        val document = repository.byId(docId) ?: return
        if (document.type != DocType.PDF && document.type != DocType.IMAGE) {
            toast(getString(R.string.files_feature_unavailable))
            return
        }
        val manager = getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .build()
        runCatching {
            manager.print(
                document.name,
                DocumentPrintAdapter(repository.fileOf(document), document.name),
                attributes,
            )
        }.onFailure { toast(getString(R.string.files_feature_unavailable)) }
    }

    private fun contentUri(document: Document): Uri =
        ShareFileProvider.uriFor(this, repository.fileOf(document))

    private fun mimeType(document: Document): String = when (document.type) {
        DocType.PDF -> "application/pdf"
        DocType.WORD ->
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        DocType.EXCEL ->
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        DocType.PPT ->
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        DocType.IMAGE -> "image/*"
        DocType.TXT -> "text/plain"
        DocType.OTHER -> "*/*"
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------ data

    private fun load() {
        val query = keyword?.trim()?.takeIf { it.isNotEmpty() }
        val sort = AppPrefs.get(this).fileSort
        lifecycleScope.launch {
            val documents = withContext(Dispatchers.IO) {
                // The documents of the device are searched as well.
                repository.syncSharedDocuments(applicationContext)
                repository.query(null, sort, query)
            }
            render(documents)
        }
    }


    private fun render(documents: List<Document>) {
        // A search hit paints the keyword in the brand colour, the rest of the
        // name keeps the colour of the design.
        val hit = keyword?.trim()?.takeIf { it.isNotEmpty() }
        val rows = documents.map { document ->
            FileListAdapter.Row(
                document = document,
                titleHighlight = hit,
                state = FileItemUiState(
                    name = document.name,
                    meta = FileUi.meta(document),
                    typeLabel = getString(document.type.labelRes),
                    typeColor = ContextCompat.getColor(
                        this,
                        FileUi.typeColor(document.type),
                    ),
                    thumbnail = thumbnails.of(this, document),
                    showThumbnail = true,
                    showType = true,
                    showMore = true,
                    showCheckbox = false,
                    encrypted = document.isEncrypted,
                ),
            )
        }
        adapter.submitList(rows)
        val empty = documents.isEmpty()
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.recycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "FileSearchActivity"
        private const val ROW_GAP = 12

        /** How long the search field waits for the keyboard of the page. */
        private const val FOCUS_BACK_DELAY_MS = 200L

        /** Opens the search page. */
        fun start(context: Context) {
            context.startActivity(Intent(context, FileSearchActivity::class.java))
        }
    }
}
