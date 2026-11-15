package com.peng.pdfreader.feature.files.fragment

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.appcore.widget.FileListItemDecoration
import com.peng.pdfreader.appcore.widget.FileItemUiState
import com.peng.pdfreader.base.BaseFragment
import com.peng.pdfreader.base.ShareFileProvider
import com.peng.pdfreader.data.model.DocType
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.data.prefs.AppPrefs
import com.peng.pdfreader.data.prefs.FileSort
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.databinding.FragmentFilesBinding
import com.peng.pdfreader.feature.lock.activity.LockActivity
import com.peng.pdfreader.feature.tools.activity.CompressActivity
import com.peng.pdfreader.feature.tools.activity.MergeActivity
import com.peng.pdfreader.feature.tools.activity.SplitActivity
import com.peng.pdfreader.feature.files.FileActions
import com.peng.pdfreader.feature.files.adapter.FileListAdapter
import com.peng.pdfreader.feature.files.dialog.DeleteConfirmDialog
import com.peng.pdfreader.feature.files.dialog.RenameDialog
import com.peng.pdfreader.feature.files.print.DocumentPrintAdapter
import com.peng.pdfreader.feature.files.sheet.FileActionSheet
import com.peng.pdfreader.feature.files.sheet.FileFilterSheet
import com.peng.pdfreader.feature.files.sheet.SortSheet
import com.peng.pdfreader.feature.files.util.FileUi
import com.peng.pdfreader.feature.files.util.PageCountDrawable
import com.peng.pdfreader.feature.preview.activity.PreviewActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Files tab: the complete document library.
 *
 * Search, type filter and sort order are persisted in [AppPrefs]; the list
 * itself is rendered by [FileListAdapter] and every row is the shared
 * [com.peng.pdfreader.appcore.widget.FileListItemView].
 */
class FilesFragment : BaseFragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: DocumentRepository
    private lateinit var prefs: AppPrefs
    private lateinit var adapter: FileListAdapter
    private lateinit var searchAction: ImageView

    /** Documents of the last query, rendered by [renderList]. */
    private var documents: List<Document> = emptyList()

    /** Ids of the documents picked in the multi select mode. */
    private val selectedIds = mutableSetOf<String>()

    private var selectionMode = false
    private var searchVisible = false
    private var keyword: String? = null

    /** The thumbnails are cached so the diff of the list stays stable. */
    private val thumbnails = mutableMapOf<String, Drawable>()

    /** Serialises the access to the repository. */
    private val repositoryLock = Mutex()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = DocumentRepository.get(requireContext())
        prefs = AppPrefs.get(requireContext())

        setupTitleBar()
        setupSearch()
        setupFilterRow()
        setupList()
        setupSelectBar()
        setupEmptyState()
        registerResults()
        load()
    }

    override fun onResume() {
        super.onResume()
        // The preview may have changed the document (progress, favourite).
        if (_binding != null) load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun handleBackPressed(): Boolean {
        if (selectionMode) {
            exitSelection()
            return true
        }
        if (searchVisible) {
            toggleSearch(false)
            return true
        }
        return false
    }

    // ------------------------------------------------------------------ setup

    private fun setupTitleBar() {
        binding.titleBar.title = getString(R.string.nav_files)
        binding.titleBar.onBackClick = { exitSelection() }

        searchAction = ImageView(requireContext()).apply {
            setImageResource(R.mipmap.ic_search_black)
            contentDescription = getString(R.string.search)
            layoutParams = FrameLayout.LayoutParams(dp(24), dp(24))
            setOnClickListener { toggleSearch(!searchVisible) }
        }
        binding.titleBar.addAction(searchAction)
    }

    private fun setupSearch() {
        binding.etSearch.doAfterTextChanged { text ->
            val value = text?.toString().orEmpty().trim()
            if (value == keyword.orEmpty()) return@doAfterTextChanged
            keyword = value.ifEmpty { null }
            load()
        }
        binding.tvSearchCancel.setOnClickListener { toggleSearch(false) }
    }

    private fun setupFilterRow() {
        binding.filterChip.setOnClickListener { FileFilterSheet.show(childFragmentManager) }
        binding.sortChip.setOnClickListener { SortSheet.show(childFragmentManager) }
    }

    private fun setupList() {
        adapter = FileListAdapter(
            object : FileListAdapter.Callbacks {
                override fun onRowClick(document: Document) {
                    if (selectionMode) {
                        toggleSelection(document.id)
                    } else {
                        PreviewActivity.start(requireContext(), document.id)
                    }
                }

                override fun onRowMore(document: Document) {
                    FileActionSheet.show(childFragmentManager, document.id)
                }

                override fun onRowChecked(document: Document, checked: Boolean) {
                    setSelected(document.id, checked)
                }

                override fun onRowLongPressed(document: Document) {
                    enterSelection(document.id)
                }
            },
        )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.recycler.setHasFixedSize(true)
        binding.recycler.addItemDecoration(FileListItemDecoration(dp(ROW_GAP)))
    }

    private fun setupSelectBar() {
        binding.btnSelectAll.setOnClickListener { toggleSelectAll() }
        binding.btnDeleteSelected.setOnClickListener {
            DeleteConfirmDialog.show(childFragmentManager, selectedIds.toList())
        }
        binding.btnCancelSelect.setOnClickListener { exitSelection() }
    }

    private fun setupEmptyState() {
        binding.emptyState.illustrationDrawable =
            ContextCompat.getDrawable(requireContext(), R.mipmap.bg_empty)
        binding.emptyState.title = getString(R.string.no_items)
        binding.emptyState.hint = getString(R.string.empty_files_hint)
    }

    private fun registerResults() {
        childFragmentManager.setFragmentResultListener(
            FileActions.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            onFileAction(bundle.getString(FileActions.KEY_ACTION), bundle.getString(FileActions.KEY_DOC_ID))
        }
        childFragmentManager.setFragmentResultListener(
            FileFilterSheet.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            val raw = bundle.getString(FileFilterSheet.KEY_FILTER)
            prefs.fileFilter = if (raw == null || raw == FileFilterSheet.FILTER_ALL) {
                null
            } else {
                DocType.entries.firstOrNull { it.name == raw }
            }
            load()
        }
        childFragmentManager.setFragmentResultListener(
            SortSheet.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            val raw = bundle.getString(SortSheet.KEY_SORT)
            prefs.fileSort = FileSort.entries.firstOrNull { it.name == raw } ?: prefs.fileSort
            load()
        }
        childFragmentManager.setFragmentResultListener(
            RenameDialog.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, _ -> load() }
        childFragmentManager.setFragmentResultListener(
            DeleteConfirmDialog.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, _ ->
            exitSelection()
            load()
        }
    }

    // ------------------------------------------------------------------ data

    private fun load() {
        if (view == null) return
        val filter = prefs.fileFilter
        val sort = prefs.fileSort
        val query = keyword?.trim()?.takeIf { it.isNotEmpty() }
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                repositoryLock.withLock { repository.query(filter, sort, query) }
            }
            documents = result
            renderList()
            updateFilterLabels()
        }
    }

    private fun renderList() {
        val rows = documents.map { document ->
            FileListAdapter.Row(
                document = document,
                state = FileItemUiState(
                    name = document.name,
                    meta = FileUi.meta(document),
                    typeLabel = getString(document.type.labelRes),
                    typeColor = ContextCompat.getColor(
                        requireContext(),
                        FileUi.typeColor(document.type),
                    ),
                    thumbnail = thumbnail(document),
                    showThumbnail = true,
                    showType = true,
                    showMore = !selectionMode,
                    showCheckbox = selectionMode,
                    checked = document.id in selectedIds,
                ),
            )
        }
        adapter.submitList(rows)

        val empty = documents.isEmpty()
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.recycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun thumbnail(document: Document): Drawable? {
        val key = "${document.type.name}_${document.pageCount}"
        val cached = thumbnails[key]
        if (cached != null) return cached
        val logo = ContextCompat.getDrawable(requireContext(), document.type.logoRes) ?: return null
        val drawable = if (document.pageCount > 0) {
            PageCountDrawable(logo, document.pageCount, requireContext())
        } else {
            logo
        }
        thumbnails[key] = drawable
        return drawable
    }

    private fun updateFilterLabels() {
        binding.tvFilterLabel.text = prefs.fileFilter
            ?.let { getString(it.labelRes) }
            ?: getString(R.string.all)
        binding.tvSortLabel.text = getString(
            when (prefs.fileSort) {
                FileSort.NAME -> R.string.sort_name
                FileSort.MODIFIED -> R.string.sort_modified
                FileSort.CREATED -> R.string.sort_created
                FileSort.SIZE -> R.string.sort_size
            },
        )
    }

    // ------------------------------------------------------------------ search

    private fun toggleSearch(show: Boolean) {
        searchVisible = show
        binding.searchBar.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            if (selectionMode) exitSelection()
            binding.etSearch.setText(keyword.orEmpty())
            binding.etSearch.setSelection(binding.etSearch.text.length)
            binding.etSearch.requestFocus()
        } else {
            // Clearing the field resets the keyword and reloads the list.
            binding.etSearch.setText("")
        }
        updateTitle()
    }

    private fun updateTitle() {
        binding.titleBar.title = when {
            selectionMode -> getString(R.string.selected_count, selectedIds.size)
            searchVisible -> ""
            else -> getString(R.string.nav_files)
        }
    }

    // ------------------------------------------------------------------ select

    private fun enterSelection(docId: String) {
        selectionMode = true
        selectedIds.add(docId)
        binding.selectBar.visibility = View.VISIBLE
        binding.titleBar.showBack = true
        searchAction.visibility = View.GONE
        updateSelectionUi()
    }

    private fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
        binding.selectBar.visibility = View.GONE
        binding.titleBar.showBack = false
        searchAction.visibility = View.VISIBLE
        updateSelectionUi()
    }

    private fun toggleSelection(docId: String) {
        setSelected(docId, docId !in selectedIds)
    }

    private fun setSelected(docId: String, selected: Boolean) {
        if (selected) selectedIds.add(docId) else selectedIds.remove(docId)
        if (selectionMode) updateSelectionUi()
    }

    private fun toggleSelectAll() {
        if (selectedIds.size == documents.size) {
            selectedIds.clear()
        } else {
            documents.forEach { selectedIds.add(it.id) }
        }
        updateSelectionUi()
    }

    private fun updateSelectionUi() {
        binding.tvSelectCount.text = getString(R.string.selected_count, selectedIds.size)
        updateTitle()
        renderList()
    }

    // ------------------------------------------------------------------ actions

    private fun onFileAction(action: String?, docId: String?) {
        if (action == null || docId == null) return
        when (action) {
            FileActions.RENAME -> RenameDialog.show(childFragmentManager, docId)
            FileActions.DELETE -> DeleteConfirmDialog.show(childFragmentManager, listOf(docId))
            FileActions.OPEN_WITH -> openWith(docId)
            FileActions.SHARE -> share(docId)
            FileActions.PRINT -> printDocument(docId)
            FileActions.SPLIT -> SplitActivity.start(requireContext(), docId)
            FileActions.COMPRESS -> CompressActivity.start(requireContext(), docId)
            FileActions.MERGE -> MergeActivity.start(requireContext())
            FileActions.LOCK -> LockActivity.start(requireContext())
            FileActions.CHANGED -> load()
            else -> toast(getString(R.string.files_feature_unavailable))
        }
    }

    private fun openWith(docId: String) {
        val document = repository.byId(docId) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri(document), mimeType(document))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (error: Exception) {
            Logger.e(TAG, "no application can open $docId", error)
            toast(getString(R.string.files_feature_unavailable))
        }
    }

    private fun share(docId: String) {
        val document = repository.byId(docId) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType(document)
            putExtra(Intent.EXTRA_STREAM, contentUri(document))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.files_share)))
        } catch (error: Exception) {
            Logger.e(TAG, "share of $docId failed", error)
            toast(getString(R.string.files_feature_unavailable))
        }
    }

    private fun printDocument(docId: String) {
        val document = repository.byId(docId) ?: return
        if (document.type != DocType.PDF && document.type != DocType.IMAGE) {
            toast(getString(R.string.files_feature_unavailable))
            return
        }
        val manager = requireContext().getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: return
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .build()
        try {
            manager.print(
                document.name,
                DocumentPrintAdapter(repository.fileOf(document), document.name),
                attributes,
            )
        } catch (error: Exception) {
            Logger.e(TAG, "print of $docId failed", error)
            toast(getString(R.string.files_feature_unavailable))
        }
    }

    /**
     * The sandbox is private to the app, so a content URI is the only way to
     * hand a document to another application.
     */
    private fun contentUri(document: Document): Uri =
        ShareFileProvider.uriFor(requireContext(), repository.fileOf(document))

    private fun mimeType(document: Document): String = when (document.type) {
        DocType.PDF -> "application/pdf"
        DocType.WORD -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        DocType.EXCEL -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        DocType.PPT -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        DocType.IMAGE -> "image/*"
        DocType.TXT -> "text/plain"
        DocType.OTHER -> "*/*"
    }

    // ------------------------------------------------------------------ helper

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "FilesFragment"
        private const val ROW_GAP = 12
    }
}
