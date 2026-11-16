package com.peng.pdfreader.ui.fragments

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.base.BaseFragment
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.AppPrefs
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.databinding.FragmentHomeBinding
import com.peng.pdfreader.databinding.HomeItemFilterBinding
import com.peng.pdfreader.ui.uiData.FileActions
import com.peng.pdfreader.ui.dialogs.DialogDeleteConfirm
import com.peng.pdfreader.ui.dialogs.BottomSheetRename
import com.peng.pdfreader.ui.dialogs.BottomSheetMoreActionPdf
import com.peng.pdfreader.ui.dialogs.BottomSheetSort
import com.peng.pdfreader.utils.FileSortOption
import com.peng.pdfreader.utils.FilesPrefs
import com.peng.pdfreader.ui.adapter.listAdapter.HomeDocumentAdapter
import com.peng.pdfreader.ui.adapter.pageAdapter.HomePagerAdapter
import com.peng.pdfreader.ui.uiData.QuickAction
import com.peng.pdfreader.utils.HomeDocumentActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Home tab.
 *
 * Shows the quick actions, the file type filter strip, the recent document
 * list and the scan button. The list itself lives in a dedicated adapter, see
 * [HomeDocumentAdapter].
 *
 * The page does not navigate on its own: every action is reported through the
 * listener properties below so that the shell can route it. The only piece of
 * UI the page owns is the document sheet of the row menu (design: home-more).
 */
class HomeFragment : BaseFragment() {

    /** Raised when a quick action card is tapped. */
    var onQuickAction: ((QuickAction) -> Unit)? = null

    /** Raised when a document row is tapped. */
    var onOpenDocument: ((Document) -> Unit)? = null

    /**
     * Raised when the "more" affordance of a document row is tapped.
     *
     * The page opens the document sheet (design: home-more) itself, the
     * callback is kept so that the shell can take over the row menu.
     */

    /** Raised when the search action of the title bar is tapped. */
    var onSearchClick: (() -> Unit)? = null

    /** Raised when the file selection action of the title bar is tapped. */
    var onSelectClick: (() -> Unit)? = null

    /** Raised when the empty state asks the user to open the system settings. */
    var onOpenSettings: (() -> Unit)? = null

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: AppPrefs

    private lateinit var pagerAdapter: HomePagerAdapter

    private val filterSegments = mutableListOf<Pair<HomeItemFilterBinding, DocType?>>()
    private var filter: DocType? = null
    /** Guards the two way sync between the strip and the pager. */
    private var syncingPager = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = AppPrefs.get(requireContext())
        filter = prefs.fileFilter?.takeIf { it in FILTER_TYPES }
        // A filter that the strip cannot show would leave the list and the
        // highlight out of sync, so it is dropped.
        if (prefs.fileFilter != filter) prefs.fileFilter = filter

        setupTitleBar()
        setupQuickActions()
        setupFilterStrip()
        setupDocumentPager()
        registerResults()
        refreshDocuments()
    }

    override fun onResume() {
        super.onResume()
        refreshDocuments()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refreshDocuments()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        filterSegments.clear()
        _binding = null
    }

    // ---------------------------------------------------------------- title bar

    private fun setupTitleBar() {
        binding.tvTitle.text = buildTitle()
        binding.ivSearch.setOnClickListener {
            onSearchClick?.invoke()
        }
        binding.ivFileSelect.setOnClickListener {
            onSelectClick?.invoke()
        }
        binding.ivSort.setOnClickListener {
            openSortSheet()
        }
    }

    /**
     * The title is the app name with the product part in the brand red, as the
     * design shows it.
     */
    private fun buildTitle(): CharSequence {
        val raw = getString(R.string.app_name)
        val marker = "PDF"
        val start = raw.indexOf(marker)
        if (start < 0) return raw
        return SpannableString(raw).apply {
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.brand_primary)),
                start,
                raw.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    // ---------------------------------------------------------------- quick actions

    private fun setupQuickActions() {

        binding.toolsImageToPdf.setOnClickListener {
            // todo 进入图片转pdf的流程
        }

        binding.toolsMergePdf.setOnClickListener {
            // todo 进入合并pdf的流程
        }

        binding.toTools.setOnClickListener {
            // todo 进入合并pdf的流程
        }

    }

    // ---------------------------------------------------------------- filter strip

    private fun setupFilterStrip() {
        filterSegments += listOf(
            binding.filterAll to null,
            binding.filterPdf to DocType.PDF,
            binding.filterWord to DocType.WORD,
            binding.filterExcel to DocType.EXCEL,
            binding.filterPpt to DocType.PPT,
        )
        filterSegments.forEachIndexed { index, (segment, type) ->
            segment.filterLabel.setText(type?.labelRes ?: R.string.all)
            segment.root.setOnClickListener { selectFilter(type) }
            segment.root.tag = index
        }
        applyFilterState()
    }

    private fun selectFilter(type: DocType?) {
        if (filter == type) return
        filter = type
        prefs.fileFilter = type
        Logger.d(TAG, "file type filter changed to ${type?.name ?: "ALL"}")
        applyFilterState()
        // The strip and the pager show the same state, so the pager follows.
        val index = pagerAdapter.filters.indexOf(type)
        if (index >= 0 && index != binding.documentPager.currentItem) {
            syncingPager = true
            binding.documentPager.setCurrentItem(index, true)
            syncingPager = false
        }
    }

    private fun applyFilterState() {
        val context = requireContext()
        filterSegments.forEach { (segment, type) ->
            val active = type == filter
            segment.filterLabel.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (active) R.color.brand_primary else R.color.text_disabled,
                ),
            )
            segment.filterIndicator.visibility = if (active) View.VISIBLE else View.INVISIBLE
        }
    }

    // ---------------------------------------------------------------- document pager

    private fun setupDocumentPager() {
        pagerAdapter = HomePagerAdapter(
            onOpen = { document -> onOpenDocument?.invoke(document) },
            onMenu = { document -> openDocumentSheet(document) },
            onEmptyAction = { openSettings() },
        )
        binding.documentPager.adapter = pagerAdapter
        binding.documentPager.offscreenPageLimit = 1
        binding.documentPager.registerOnPageChangeCallback(pageChangeCallback)
        // Start on the page of the stored filter.
        val index = pagerAdapter.filters.indexOf(filter)
        if (index >= 0) binding.documentPager.setCurrentItem(index, false)
    }

    // ---------------------------------------------------------------- document sheet

    /**
     * The row menu of the design (home-more).
     *
     * The sheet of the files feature carries the header, the four quick
     * actions and the options card of the design, so it is reused here.
     */
    private fun openDocumentSheet(document: Document) {
        BottomSheetMoreActionPdf.show(childFragmentManager, document.id)
    }

    /**
     * Listens to the operations of the document sheet.
     *
     * The sheet reports the picked operation, the page runs it and refreshes
     * the list when the document changed.
     */
    private fun registerResults() {
        childFragmentManager.setFragmentResultListener(
            FileActions.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            val action = bundle.getString(FileActions.KEY_ACTION) ?: return@setFragmentResultListener
            val docId = bundle.getString(FileActions.KEY_DOC_ID) ?: return@setFragmentResultListener
            HomeDocumentActions(this@HomeFragment).run(action, docId)
            refreshDocuments()
        }
        childFragmentManager.setFragmentResultListener(
            BottomSheetRename.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, _ -> refreshDocuments() }
        childFragmentManager.setFragmentResultListener(
            DialogDeleteConfirm.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, _ -> refreshDocuments() }
        // The sort sheet writes the picked order back, the list follows it.
        childFragmentManager.setFragmentResultListener(
            BottomSheetSort.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            val raw = bundle.getString(BottomSheetSort.KEY_SORT)
            val option = raw?.let { name ->
                FileSortOption.entries.firstOrNull { it.name == name }
            } ?: return@setFragmentResultListener
            FilesPrefs.get(requireContext()).sortOption = option
            // The base sort is shared with the rest of the app.
            prefs.fileSort = option.sort
            Logger.d(TAG, "file sort changed to ${option.name}")
            refreshDocuments()
        }
    }

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            if (syncingPager) return
            val type = pagerAdapter.filters.getOrNull(position)
            if (type != filter) {
                filter = type
                prefs.fileFilter = type
                Logger.d(TAG, "swiped to ${type?.name ?: "ALL"}")
                applyFilterState()
            }
        }
    }

    private fun refreshDocuments() {
        if (_binding == null || !::pagerAdapter.isInitialized) return
        // The list follows the sort order of the files feature.
        val option = FilesPrefs.get(requireContext()).sortOption
        viewLifecycleOwner.lifecycleScope.launch {
            val repository = DocumentRepository.get(requireContext())
            val documents = withContext(Dispatchers.IO) {
                // The documents of the device are part of the list as well.
                repository.syncSharedDocuments(requireContext().applicationContext)
                repository.query(null, option.sort, null)
            }
            if (_binding == null || !::pagerAdapter.isInitialized) return@launch
            pagerAdapter.submit(if (option.reverse) documents.reversed() else documents)
        }
    }

    // ------------------------------------------------------------------ sort sheet

    /**
     * The sort sheet of the files feature.
     *
     * The sheet writes the picked order back, the list is rebuilt with it.
     */
    private fun openSortSheet() {
        BottomSheetSort.show(childFragmentManager)
    }

    // ---------------------------------------------------------------- empty state

    /**
     * "Go to set" opens the system page of this app; when the shell wants to
     * handle it itself it takes over through [onOpenSettings].
     */
    private fun openSettings() {
        val callback = onOpenSettings
        if (callback != null) {
            callback.invoke()
            return
        }
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", requireContext().packageName, null),
        )
        runCatching { startActivity(intent) }
            .onFailure { Logger.e(TAG, "cannot open the system settings", it) }
    }

    // ---------------------------------------------------------------- helpers

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** Keeps a constant gap between the cards of a horizontal row. */
    private class GapDecoration(private val gap: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            if (parent.getChildAdapterPosition(view) > 0) outRect.left = gap
        }
    }

    companion object {
        private const val TAG = "HomeFragment"
        private const val ACTION_SIZE = 24
        private const val ACTION_GAP = 16
        private const val QUICK_ACTION_GAP = 12

        /** The file types the filter strip can show. */
        private val FILTER_TYPES = listOf(
            DocType.PDF,
            DocType.WORD,
            DocType.EXCEL,
            DocType.PPT,
        )
    }
}
