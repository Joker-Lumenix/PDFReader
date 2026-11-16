package com.peng.pdfreader.feature.files.fragment

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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.appcore.util.SystemBarInsets
import com.peng.pdfreader.appcore.util.Thumbnails
import com.peng.pdfreader.appcore.widget.FileItemUiState
import com.peng.pdfreader.appcore.widget.FileListItemDecoration
import com.peng.pdfreader.base.BaseFragment
import com.peng.pdfreader.base.ShareFileProvider
import com.peng.pdfreader.data.model.DocType
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.data.prefs.AppPrefs
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.databinding.FragmentFilesBinding
import com.peng.pdfreader.feature.files.FileActions
import com.peng.pdfreader.feature.files.adapter.AdBannerAdapter
import com.peng.pdfreader.feature.files.adapter.FileListAdapter
import com.peng.pdfreader.feature.files.dialog.DeleteConfirmDialog
import com.peng.pdfreader.feature.files.dialog.RenameDialog
import com.peng.pdfreader.feature.files.print.DocumentPrintAdapter
import com.peng.pdfreader.feature.files.sheet.FileActionSheet
import com.peng.pdfreader.feature.files.sheet.FileFilterMenu
import com.peng.pdfreader.feature.files.sheet.SortSheet
import com.peng.pdfreader.feature.files.util.FileScope
import com.peng.pdfreader.feature.files.util.FileSortOption
import com.peng.pdfreader.feature.files.util.FileUi
import com.peng.pdfreader.feature.files.util.FilesPrefs
import com.peng.pdfreader.feature.files.util.PageCountDrawable
import com.peng.pdfreader.feature.lock.activity.LockActivity
import com.peng.pdfreader.feature.preview.activity.PreviewActivity
import com.peng.pdfreader.feature.settings.widget.AdSamples
import com.peng.pdfreader.feature.tools.activity.CompressActivity
import com.peng.pdfreader.feature.tools.activity.MergeActivity
import com.peng.pdfreader.feature.tools.activity.SplitActivity
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
 * [com.peng.pdfreader.appcore.widget.FileListItemView].
 */
class FilesFragment : BaseFragment() {

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

    private var selectionMode = false
    private var searchVisible = false
    private var keyword: String? = null
    private var scope = FileScope.ALL

    /** The action that opens the type filter popover. */
    private var filterAction: View? = null

    /** The share action of the title bar. */
    private var shareAction: View? = null

    /** The print action of the title bar. */
    private var printAction: View? = null

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
        filesPrefs = FilesPrefs.get(requireContext())
        scope = filesPrefs.scope

        setupTitleBar()
        setupScopeTabs()
        setupSearch()
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
        filterAction = null
        shareAction = null
        printAction = null
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
        // The design draws the back arrow half transparent (files-Selected).
        binding.titleBar.findViewById<ImageView>(R.id.ivBack)?.imageTintList =
            ColorStateList.valueOf(Color.argb(128, 39, 38, 54))

        // The design puts three actions on the right hand side of the title
        // bar: share (x=236), print (x=276) and the type filter switch
        // (x=316), every icon 24dp wide with a 16dp gap.
        val share = ImageView(requireContext()).apply {
            setImageResource(R.mipmap.ic_share_red)
            contentDescription = getString(R.string.files_share)
            setOnClickListener { shareListed() }
        }
        val print = ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_print_red)
            contentDescription = getString(R.string.files_print)
            setOnClickListener { printListed() }
        }
        val filter = ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_switch_red)
            contentDescription = getString(R.string.all)
            setOnClickListener { openFilterMenu() }
        }
        val actions = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(share, actionParams(endMargin = dp(16)))
            addView(print, actionParams(endMargin = dp(16)))
            addView(filter, actionParams())
        }
        binding.titleBar.addAction(actions)
        shareAction = share
        printAction = print
        filterAction = filter
    }

    private fun actionParams(endMargin: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = endMargin }

    private fun setupScopeTabs() {
        binding.tabAll.setOnClickListener { setScope(FileScope.ALL) }
        binding.tabRecent.setOnClickListener { setScope(FileScope.RECENT) }
        binding.tabFavorites.setOnClickListener { setScope(FileScope.FAVORITES) }
        binding.ivSearch.setOnClickListener { toggleSearch(true) }
        renderScope()
    }

    private fun setupSearch() {
        // The search field replaces the title strip but has to stay below the
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
        binding.tvSearchCancel.setOnClickListener { toggleSearch(false) }
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
        // The ad banner of the design is the first entry of the list, it
        // scrolls with the documents.
        binding.recycler.adapter = ConcatAdapter(
            AdBannerAdapter(AdSamples.banner(requireContext())),
            adapter,
        )
        binding.recycler.setHasFixedSize(true)
        binding.recycler.addItemDecoration(FileListItemDecoration(dp(ROW_GAP)))
    }

    private fun setupSelectBar() {
        binding.actionSelectShare.setOnClickListener { shareSelected() }
        binding.actionSelectSecond.setOnClickListener {
            if (selectedIds.size == 1) renameSelected() else mergeSelected()
        }
        binding.actionSelectEdit.setOnClickListener { editSelected() }
        binding.actionSelectDelete.setOnClickListener {
            DeleteConfirmDialog.show(childFragmentManager, selectedIds.toList())
        }
    }

    private fun setupEmptyState() {
        binding.emptyState.illustrationDrawable =
            ContextCompat.getDrawable(requireContext(), R.mipmap.bg_empty)
        // The design draws the illustration 148x148.
        binding.emptyState.findViewById<ImageView>(R.id.ivIllustration)?.let { illustration ->
            illustration.layoutParams = illustration.layoutParams.apply {
                width = dp(EMPTY_ILLUSTRATION)
                height = dp(EMPTY_ILLUSTRATION)
            }
        }
        renderEmptyState()
    }

    private fun registerResults() {
        childFragmentManager.setFragmentResultListener(
            FileActions.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            onFileAction(bundle.getString(FileActions.KEY_ACTION), bundle.getString(FileActions.KEY_DOC_ID))
        }
        childFragmentManager.setFragmentResultListener(
            SortSheet.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            val raw = bundle.getString(SortSheet.KEY_SORT)
            val option = raw?.let { name ->
                FileSortOption.entries.firstOrNull { it.name == name }
            } ?: return@setFragmentResultListener
            filesPrefs.sortOption = option
            // The base sort is shared with the rest of the app.
            prefs.fileSort = option.sort
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
        val option = filesPrefs.sortOption
        val query = keyword?.trim()?.takeIf { it.isNotEmpty() }
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                repositoryLock.withLock {
                    val base = repository.query(filter, option.sort, query)
                    val scoped = applyScope(base)
                    if (option.reverse) scoped.reversed() else scoped
                }
            }
            documents = result
            renderList()
        }
    }

    /** Narrows the query down to the active tab. */
    private fun applyScope(list: List<Document>): List<Document> = when (scope) {
        FileScope.ALL -> list
        FileScope.RECENT -> list
            .filter { it.lastReadAt > 0L }
            .sortedByDescending { it.lastReadAt }
        FileScope.FAVORITES -> list.filter { it.isFavorite }
    }

    private fun renderList() {
        // A search hit paints the name in the light brand colour (design:
        // files-搜索).
        val hitColor = if (!keyword.isNullOrBlank()) {
            ContextCompat.getColor(requireContext(), R.color.brand_primary_light)
        } else {
            null
        }
        val rows = documents.map { document ->
            FileListAdapter.Row(
                document = document,
                titleColor = hitColor,
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
                    checkboxAtEnd = true,
                    checked = document.id in selectedIds,
                ),
            )
        }
        adapter.submitList(rows)

        renderEmptyState()
        val empty = documents.isEmpty()
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.recycler.visibility = if (empty) View.GONE else View.VISIBLE
        updateScopeRow()
    }

    private fun thumbnail(document: Document): Drawable? {
        // Raster documents show their own picture, the rest use the type logo.
        if (document.type == DocType.IMAGE) {
            val key = "image_${document.path}"
            val cached = thumbnails[key]
            if (cached != null) return cached
            val picture = Thumbnails.load(requireContext(), repository.fileOf(document))
            if (picture != null) {
                thumbnails[key] = picture
                return picture
            }
        }
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

    // ------------------------------------------------------------------ scope

    private fun setScope(value: FileScope) {
        scope = value
        filesPrefs.scope = value
        renderScope()
        load()
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
        FileFilterMenu.show(binding.root, anchor, prefs.fileFilter) { type ->
            prefs.fileFilter = type
            load()
        }
    }

    // ------------------------------------------------------------------ search

    private fun toggleSearch(show: Boolean) {
        searchVisible = show
        binding.searchBar.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            if (selectionMode) exitSelection()
            binding.searchBar.requestApplyInsets()
            binding.etSearch.setText(keyword.orEmpty())
            binding.etSearch.setSelection(binding.etSearch.text.length)
            binding.etSearch.requestFocus()
        } else {
            // Clearing the field resets the keyword and reloads the list.
            binding.etSearch.setText("")
        }
        renderEmptyState()
        updateTitle()
        updateTitleBarActions()
        updateScopeRow()
    }

    /**
     * The scope row only belongs to the plain list.
     *
     * The search field and the selection take the space over, the empty state
     * keeps the row (design: files-搜索 / files-Selected / files空).
     */
    private fun updateScopeRow() {
        val visible = !searchVisible && !selectionMode
        binding.tabRow.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /** Share and print disappear while the selection is active. */
    private fun updateTitleBarActions() {
        val visible = !searchVisible
        shareAction?.visibility = if (visible && !selectionMode) View.VISIBLE else View.GONE
        printAction?.visibility = if (visible && !selectionMode) View.VISIBLE else View.GONE
        filterAction?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updateTitle() {
        binding.titleBar.title = when {
            selectionMode -> getString(R.string.selected_count, selectedIds.size)
            searchVisible -> ""
            else -> getString(R.string.nav_files)
        }
    }

    private fun renderEmptyState() {
        val searching = !keyword.isNullOrBlank()
        binding.emptyState.title = getString(
            if (searching) R.string.files_search_empty_title else R.string.files_empty_title,
        )
        binding.emptyState.hint = getString(
            if (searching) R.string.files_search_empty_hint else R.string.files_empty_hint,
        )
    }

    // ------------------------------------------------------------------ select

    private fun enterSelection(docId: String) {
        selectionMode = true
        selectedIds.add(docId)
        binding.selectBarDivider.visibility = View.VISIBLE
        binding.selectBar.visibility = View.VISIBLE
        binding.titleBar.showBack = true
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
        binding.titleBar.showBack = false
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
            FileActions.RENAME -> RenameDialog.show(childFragmentManager, docId)
            FileActions.DELETE -> DeleteConfirmDialog.show(childFragmentManager, listOf(docId))
            FileActions.OPEN_WITH -> openWith(docId)
            FileActions.SHARE -> share(docId)
            FileActions.PRINT -> printDocument(docId)
            FileActions.SPLIT -> SplitActivity.start(requireContext(), docId)
            FileActions.COMPRESS -> CompressActivity.start(requireContext(), docId)
            FileActions.MERGE -> MergeActivity.start(requireContext())
            FileActions.LOCK -> LockActivity.start(requireContext())
            FileActions.SORT -> SortSheet.show(childFragmentManager)
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

    /** The share action of the title bar hands over every listed document. */
    private fun shareListed() {
        if (documents.isEmpty()) {
            toast(getString(R.string.files_feature_unavailable))
            return
        }
        shareDocuments(documents)
    }

    /** The print action of the title bar prints the first listed document. */
    private fun printListed() {
        val document = documents.firstOrNull()
        if (document == null) {
            toast(getString(R.string.files_feature_unavailable))
            return
        }
        printDocument(document.id)
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
        RenameDialog.show(childFragmentManager, docId)
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
