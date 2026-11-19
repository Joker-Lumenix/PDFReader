package com.peng.pdfreader.ui.fragments

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.peng.pdfreader.R
import com.peng.pdfreader.core.util.Logger
import com.peng.pdfreader.core.widget.EmptyStateView
import com.peng.pdfreader.core.widget.FileItemUiState
import com.peng.pdfreader.core.widget.FileListItemDecoration
import com.peng.pdfreader.base.BaseFragment
import com.peng.pdfreader.base.ShareFileProvider
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.AppPrefs
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.data.SharedStorage
import com.peng.pdfreader.databinding.FilesPageScopeBinding
import com.peng.pdfreader.databinding.FragmentFilesBinding
import com.peng.pdfreader.ui.uiData.FileActions
import com.peng.pdfreader.ui.adapter.listAdapter.AdBannerAdapter
import com.peng.pdfreader.ui.adapter.listAdapter.DocumentPrintAdapter
import com.peng.pdfreader.ui.dialogs.DocumentActionSheet
import com.peng.pdfreader.ui.dialogs.PopWindowFileFilter
import com.peng.pdfreader.ui.dialogs.BottomSheetSort
import com.peng.pdfreader.ui.dialogs.DocumentCipher
import com.peng.pdfreader.utils.DocumentThumbnails
import com.peng.pdfreader.utils.FileScope
import com.peng.pdfreader.utils.FileSortOption
import com.peng.pdfreader.utils.FileUi
import com.peng.pdfreader.utils.FilesPrefs
import com.peng.pdfreader.ui.activities.PreviewActivity
import com.peng.pdfreader.ui.uiData.AdSamples
import com.peng.pdfreader.ui.activities.CompressActivity
import com.peng.pdfreader.ui.activities.FileSearchActivity
import com.peng.pdfreader.ui.activities.FileSelectActivity
import com.peng.pdfreader.ui.activities.MergeActivity
import com.peng.pdfreader.ui.activities.PermissionActivity
import com.peng.pdfreader.ui.activities.SplitActivity
import com.peng.pdfreader.ui.adapter.listAdapter.FileListAdapter
import com.peng.pdfreader.ui.dialogs.DialogDeleteConfirm
import com.peng.pdfreader.ui.dialogs.BottomSheetRename
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Files tab: the complete document library.
 *
 * The header carries the scope tabs (全部 / 最近 / 收藏) and the three actions
 * of the design (分享 / 打印 / 类型筛选). Search replaces the title bar with a
 * search field, exactly as the design does, and the multi select mode replaces
 * the scope row with the action bar.
 *
 * The type filter and the sort order are persisted in [AppPrefs]; the scope
 * and the direction of the sort live in [FilesPrefs]. The list itself is
 * rendered by [FileListAdapter] and every row is the shared
 * [com.peng.pdfreader.core.widget.FileListItemView].
 */
class FilesFragment : BaseFragment() {

    /**
     * The tab sits above the bottom navigation bar of the shell, the bar takes
     * the inset of the navigation bar itself.
     */
    override val takesBottomInset: Boolean = false

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: DocumentRepository
    private lateinit var prefs: AppPrefs
    private lateinit var filesPrefs: FilesPrefs
    private lateinit var adapter: FileListAdapter

    /** Documents of the last query, rendered by [renderList]. */
    private var documents: List<Document> = emptyList()

    /** Ids of the documents picked in the multi select mode. */
    private val selectedIds = mutableSetOf<String>()

    /** The scopes of the tab, they are the pages of the pager. */
    private val scopePages = listOf(FileScope.ALL, FileScope.RECENT, FileScope.FAVORITES)

    /** The list of every page of the pager. */
    private val pageAdapters = mutableMapOf<Int, FileListAdapter>()

    /** The empty state of every page of the pager. */
    private val pageEmptyStates = mutableMapOf<Int, EmptyStateView>()

    private var selectionMode = false
    private var scope = FileScope.ALL

    /** The action that opens the type filter popover, next to the scope tabs. */
    private var filterAction: View? = null

    /** The search action of the title bar. */
    private var searchAction: View? = null

    /** The file selection action of the title bar. */
    private var selectAction: View? = null

    /** The sort action of the title bar. */
    private var sortAction: View? = null

    /** The thumbnails are cached so the diff of the list stays stable. */
    private val documentThumbnails by lazy { DocumentThumbnails(repository) }

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
        filesPrefs = FilesPrefs.get(requireContext())
        scope = filesPrefs.scope

        setupTitleBar()
        setupScopeTabs()
        setupPager()
        setupSelectBar()
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
        filterAction = null
        searchAction = null
        selectAction = null
        sortAction = null
        _binding = null
    }

    override fun handleBackPressed(): Boolean {
        if (selectionMode) {
            exitSelection()
            return true
        }
        return false
    }

    // ------------------------------------------------------------------ setup

    private fun setupTitleBar() {
        binding.tvTitle.text = getString(R.string.nav_files)
        binding.ivBack.setOnClickListener { exitSelection() }
        // The design draws the back arrow half transparent (files-Selected).
        binding.ivBack.imageTintList =
            ColorStateList.valueOf(Color.argb(128, 39, 38, 54))

        // The three actions of the title bar (design: files):
        // the search opens the search page, the file selection opens the
        // selection page and the sort opens the rules of the order.
        binding.ivSearch.setOnClickListener { openSearchPage() }
        binding.ivSelect.setOnClickListener { openSelectPage() }
        binding.ivSort.setOnClickListener { openSortSheet() }
        searchAction = binding.ivSearch
        selectAction = binding.ivSelect
        sortAction = binding.ivSort
    }

    private fun setupScopeTabs() {
        binding.tabAll.setOnClickListener { setScope(FileScope.ALL) }
        binding.tabRecent.setOnClickListener { setScope(FileScope.RECENT) }
        binding.tabFavorites.setOnClickListener { setScope(FileScope.FAVORITES) }
        // The icon next to the scope tabs filters by the type of the file
        // (design: files-筛选), the search lives in its own page.
        binding.ivFilter.setOnClickListener { openFilterMenu() }
        filterAction = binding.ivFilter
        renderScope()
    }

    /**
     * The tabs 全部 / 最近 / 收藏 are pages of a pager.
     *
     * The list can be swiped horizontally, the tab row follows the pager and
     * the other way round.
     */
    private fun setupPager() {
        binding.pager.adapter = ScopePagerAdapter()
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val value = scopePages[position]
                if (scope == value) return
                scope = value
                filesPrefs.scope = value
                renderScope()
            }
        })
        binding.pager.setCurrentItem(scopePages.indexOf(scope).coerceAtLeast(0), false)
    }

    /** One page of the pager, it carries its own list and its own empty state. */
    private inner class ScopePagerAdapter : RecyclerView.Adapter<PageHolder>() {

        override fun getItemCount(): Int = scopePages.size

        override fun getItemViewType(position: Int): Int = position

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            val page = FilesPageScopeBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
            val listAdapter = buildAdapter()
            page.recycler.layoutManager = LinearLayoutManager(parent.context)
            // The ad banner of the design is the first entry of the list, it
            // scrolls with the documents.
            page.recycler.adapter = ConcatAdapter(
                AdBannerAdapter(AdSamples.banner(parent.context)),
                listAdapter,
            )
            page.recycler.setHasFixedSize(true)
            page.recycler.addItemDecoration(FileListItemDecoration(dp(ROW_GAP)))
            pageAdapters[viewType] = listAdapter
            pageEmptyStates[viewType] = page.emptyState
            page.emptyState.illustrationDrawable =
                ContextCompat.getDrawable(parent.context, R.mipmap.bg_empty)
            return PageHolder(page, viewType)
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            renderPage(position)
        }
    }

    /** One page of the pager, it carries its own list and its own empty state. */
    private class PageHolder(
        val page: FilesPageScopeBinding,
        val index: Int,
    ) : RecyclerView.ViewHolder(page.root)

    /** The list of a page, every page shares the callbacks of the tab. */
    private fun buildAdapter() = FileListAdapter(
        object : FileListAdapter.Callbacks {
            override fun onRowClick(document: Document) {
                if (selectionMode) {
                    toggleSelection(document.id)
                } else {
                    PreviewActivity.start(requireContext(), document.id)
                }
            }

            override fun onRowMore(document: Document) {
                DocumentActionSheet.show(requireContext(), childFragmentManager, document)
            }

            override fun onRowChecked(document: Document, checked: Boolean) {
                setSelected(document.id, checked)
            }

            override fun onRowLongPressed(document: Document) {
                enterSelection(document.id)
            }
        },
    )

    private fun setupSelectBar() {
        binding.actionSelectShare.setOnClickListener { shareSelected() }
        binding.actionSelectSecond.setOnClickListener {
            if (selectedIds.size == 1) renameSelected() else mergeSelected()
        }
        binding.actionSelectEdit.setOnClickListener { editSelected() }
        binding.actionSelectDelete.setOnClickListener {
            DialogDeleteConfirm.show(childFragmentManager, selectedIds.toList())
        }
    }

    private fun registerResults() {
        childFragmentManager.setFragmentResultListener(
            FileActions.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            onFileAction(bundle.getString(FileActions.KEY_ACTION), bundle.getString(FileActions.KEY_DOC_ID))
        }
        childFragmentManager.setFragmentResultListener(
            BottomSheetSort.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            val raw = bundle.getString(BottomSheetSort.KEY_SORT)
            val option = raw?.let { name ->
                FileSortOption.entries.firstOrNull { it.name == name }
            } ?: return@setFragmentResultListener
            filesPrefs.sortOption = option
            // The base sort is shared with the rest of the app.
            prefs.fileSort = option.sort
            load()
        }
        childFragmentManager.setFragmentResultListener(
            BottomSheetRename.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, _ -> load() }
        childFragmentManager.setFragmentResultListener(
            DialogDeleteConfirm.REQUEST_KEY,
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
        val option = filesPrefs.sortOption
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                repositoryLock.withLock {
                    // The documents of the device come first, the app only
                    // needs the access to all files for that.
                    repository.syncSharedDocuments(requireContext().applicationContext)
                    val base = repository.query(filter, option.sort, null)
                    if (option.reverse) base.reversed() else base
                }
            }
            documents = result
            renderList()
        }
    }

    /** Narrows the query down to a scope of the tab. */
    private fun applyScope(list: List<Document>, value: FileScope): List<Document> = when (value) {
        FileScope.ALL -> list
        FileScope.RECENT -> list
            .filter { it.lastReadAt > 0L }
            .sortedByDescending { it.lastReadAt }
        FileScope.FAVORITES -> list.filter { it.isFavorite }
    }

    private fun renderList() {
        scopePages.forEachIndexed { index, value -> renderPage(index, value) }
        updateScopeRow()
    }

    /** Fills the list of one page of the pager. */
    private fun renderPage(index: Int, value: FileScope = scopePages[index]) {
        val scoped = applyScope(documents, value)
        val rows = scoped.map { document ->
            val checked = document.id in selectedIds
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
                    // The design shows the "more" affordance on every row that
                    // is not selected, the selection mark takes its place.
                    showMore = !checked,
                    showCheckbox = checked,
                    checkboxAtEnd = true,
                    checked = checked,
                    encrypted = document.isEncrypted,
                ),
            )
        }
        pageAdapters[index]?.submitList(rows)

        val emptyState = pageEmptyStates[index] ?: return
        val empty = scoped.isEmpty()
        emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        renderEmptyState(emptyState)
    }

    private fun thumbnail(document: Document): Drawable? =
        documentThumbnails.of(requireContext(), document)

    // ------------------------------------------------------------------ scope

    private fun setScope(value: FileScope) {
        scope = value
        filesPrefs.scope = value
        renderScope()
        // The scope is a page of the pager, the list is swiped to it.
        val index = scopePages.indexOf(value)
        if (index >= 0 && binding.pager.currentItem != index) {
            binding.pager.setCurrentItem(index, true)
        }
    }

    private fun renderScope() {
        val active = ContextCompat.getColor(requireContext(), R.color.brand_primary)
        val inactive = ContextCompat.getColor(requireContext(), R.color.text_disabled)

        binding.indTabAll.background = scopeIndicator()
        binding.indTabRecent.background = scopeIndicator()
        binding.indTabFavorites.background = scopeIndicator()

        binding.tvTabAll.setTextColor(if (scope == FileScope.ALL) active else inactive)
        binding.tvTabRecent.setTextColor(if (scope == FileScope.RECENT) active else inactive)
        binding.tvTabFavorites.setTextColor(if (scope == FileScope.FAVORITES) active else inactive)

        binding.indTabAll.visibility = if (scope == FileScope.ALL) View.VISIBLE else View.GONE
        binding.indTabRecent.visibility = if (scope == FileScope.RECENT) View.VISIBLE else View.GONE
        binding.indTabFavorites.visibility =
            if (scope == FileScope.FAVORITES) View.VISIBLE else View.GONE
    }

    /** The 44x2 pill that marks the active tab (design: files). */
    private fun scopeIndicator(): GradientDrawable = GradientDrawable().apply {
        setColor(ContextCompat.getColor(requireContext(), R.color.brand_primary))
        cornerRadius = dp(1).toFloat()
    }

    // ------------------------------------------------------------------ filter

    private fun openFilterMenu() {
        val anchor = filterAction ?: return
        PopWindowFileFilter.show(binding.root, anchor, prefs.fileFilter) { type ->
            prefs.fileFilter = type
            load()
        }
    }

    // ------------------------------------------------------------------ pages

    /** The search page of the design (files-搜索). */
    private fun openSearchPage() {
        FileSearchActivity.start(requireContext())
    }

    /** The page that picks files of the library (files-Selected). */
    private fun openSelectPage() {
        FileSelectActivity.start(requireContext())
    }

    /** The rules of the order of the list. */
    private fun openSortSheet() {
        BottomSheetSort.show(childFragmentManager)
    }

    /**
     * The scope row only belongs to the plain list.
     *
     * The selection takes the space over, the empty state keeps the row
     * (design: files-Selected / files空).
     */
    private fun updateScopeRow() {
        binding.tabRow.visibility = if (selectionMode) View.GONE else View.VISIBLE
    }

    /** The three actions of the title bar disappear while the selection is active. */
    private fun updateTitleBarActions() {
        val visible = !selectionMode
        searchAction?.visibility = if (visible) View.VISIBLE else View.GONE
        selectAction?.visibility = if (visible) View.VISIBLE else View.GONE
        sortAction?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updateTitle() {
        binding.tvTitle.text = when {
            selectionMode -> getString(R.string.selected_count, selectedIds.size)
            else -> getString(R.string.nav_files)
        }
    }

    private fun renderEmptyState(emptyState: EmptyStateView) {
        emptyState.title = getString(R.string.files_empty_title)
        // Without the file access the app cannot see the documents of the
        // device - the empty state says so and offers the permission page.
        if (SharedStorage.canRead(requireContext())) {
            emptyState.hint = getString(R.string.files_empty_hint)
            emptyState.actionText = null
            emptyState.onActionClick = null
        } else {
            emptyState.hint = getString(R.string.authorization_required)
            emptyState.actionText = getString(R.string.home_empty_action)
            emptyState.onActionClick = { openPermissionPage() }
        }
    }

    /** The page that asks for the access to the files. */
    private fun openPermissionPage() {
        if (SharedStorage.canRead(requireContext())) {
            // The access to the files is granted already, the page of the
            // permission has nothing left to show.
            return
        }
        startActivity(Intent(requireContext(), PermissionActivity::class.java))
    }

    // ------------------------------------------------------------------ select

    private fun enterSelection(docId: String) {
        selectionMode = true
        selectedIds.add(docId)
        binding.selectBarDivider.visibility = View.VISIBLE
        binding.selectBar.visibility = View.VISIBLE
        binding.ivBack.visibility = View.VISIBLE
        // The design drops the scope row while the selection is active.
        updateScopeRow()
        updateTitleBarActions()
        updateSelectionUi()
    }

    private fun exitSelection() {
        selectionMode = false
        selectedIds.clear()
        binding.selectBarDivider.visibility = View.GONE
        binding.selectBar.visibility = View.GONE
        binding.ivBack.visibility = View.GONE
        updateScopeRow()
        updateTitleBarActions()
        updateSelectionUi()
    }

    private fun toggleSelection(docId: String) {
        setSelected(docId, docId !in selectedIds)
    }

    private fun setSelected(docId: String, selected: Boolean) {
        if (selected) selectedIds.add(docId) else selectedIds.remove(docId)
        if (selectionMode) updateSelectionUi()
    }

    /**
     * The action bar follows the design: one selection offers
     * 分享 / 重命名 / 编辑 / 删除, several offer 分享 / 合并 / 删除.
     */
    private fun updateSelectionUi() {
        val single = selectedIds.size == 1
        binding.ivActionSelectSecond.setImageResource(
            if (single) R.mipmap.ic_rename_black else R.mipmap.ic_merge_black,
        )
        binding.tvActionSelectSecond.setText(
            if (single) R.string.rename else R.string.files_action_merge,
        )
        binding.actionSelectEdit.visibility = if (single) View.VISIBLE else View.GONE
        updateTitle()
        renderList()
    }

    // ------------------------------------------------------------------ actions

    private fun onFileAction(action: String?, docId: String?) {
        if (action == null || docId == null) return
        when (action) {
            FileActions.RENAME -> BottomSheetRename.show(childFragmentManager, docId)
            FileActions.DELETE -> DialogDeleteConfirm.show(childFragmentManager, listOf(docId))
            FileActions.OPEN_WITH -> openWith(docId)
            FileActions.SHARE -> share(docId)
            FileActions.PRINT -> printDocument(docId)
            FileActions.SPLIT -> SplitActivity.start(requireContext(), docId)
            FileActions.COMPRESS -> CompressActivity.start(requireContext(), docId)
            FileActions.MERGE -> MergeActivity.start(requireContext())
            // The sheet knows its file, so the cipher runs on it right away:
            // the password sheet asks for the password, no page in between.
            FileActions.LOCK -> DocumentCipher(this).encrypt(docId)
            FileActions.UNLOCK -> DocumentCipher(this).decrypt(docId)
            FileActions.SORT -> BottomSheetSort.show(childFragmentManager)
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

    /** The share action of the selection bar. */
    private fun shareSelected() {
        val docs = selectedIds.mapNotNull { repository.byId(it) }
        if (docs.isEmpty()) {
            toast(getString(R.string.files_feature_unavailable))
            return
        }
        shareDocuments(docs)
    }

    private fun shareDocuments(docs: List<Document>) {
        val uris = docs.map { contentUri(it) }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (docs.size == 1) mimeType(docs.first()) else "*/*"
            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.files_share)))
        } catch (error: Exception) {
            Logger.e(TAG, "share of ${docs.size} documents failed", error)
            toast(getString(R.string.files_feature_unavailable))
        }
    }

    private fun renameSelected() {
        val docId = selectedIds.firstOrNull() ?: return
        BottomSheetRename.show(childFragmentManager, docId)
    }

    private fun mergeSelected() {
        MergeActivity.start(requireContext())
    }

    private fun editSelected() {
        val docId = selectedIds.firstOrNull() ?: return
        PreviewActivity.start(requireContext(), docId)
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
        private const val EMPTY_ILLUSTRATION = 148
    }
}
