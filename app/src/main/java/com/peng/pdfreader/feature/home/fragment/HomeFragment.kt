package com.peng.pdfreader.feature.home.fragment

import android.content.Intent
import android.content.res.ColorStateList
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
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.appcore.widget.FileListItemDecoration
import com.peng.pdfreader.base.BaseFragment
import com.peng.pdfreader.data.model.DocType
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.data.prefs.AppPrefs
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.databinding.FragmentHomeBinding
import com.peng.pdfreader.databinding.HomeItemFilterBinding
import com.peng.pdfreader.feature.home.adapter.HomeDocumentAdapter
import com.peng.pdfreader.feature.home.adapter.HomeQuickActionAdapter
import com.peng.pdfreader.feature.home.model.QuickAction

/**
 * Home tab.
 *
 * Shows the quick actions, the file type filter strip, the recent document
 * list and the scan button. The list itself lives in a dedicated adapter, see
 * [HomeDocumentAdapter].
 *
 * The page never navigates on its own: every action is reported through the
 * listener properties below so that the shell can route it.
 */
class HomeFragment : BaseFragment() {

    /** Raised when a quick action card is tapped. */
    var onQuickAction: ((QuickAction) -> Unit)? = null

    /** Raised when a document row is tapped. */
    var onOpenDocument: ((Document) -> Unit)? = null

    /** Raised when the "more" affordance of a document row is tapped. */
    var onDocumentMenu: ((Document) -> Unit)? = null

    /** Raised when the scan button is tapped. */
    var onScanClick: (() -> Unit)? = null

    /** Raised when the share action of the title bar is tapped. */
    var onShareClick: (() -> Unit)? = null

    /** Raised when the print action of the title bar is tapped. */
    var onPrintClick: (() -> Unit)? = null

    /** Raised when the overflow action of the title bar is tapped. */
    var onMoreClick: (() -> Unit)? = null

    /** Raised when the empty state asks the user to open the system settings. */
    var onOpenSettings: (() -> Unit)? = null

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: AppPrefs
    private lateinit var quickActionAdapter: HomeQuickActionAdapter
    private lateinit var documentAdapter: HomeDocumentAdapter

    private val filterSegments = mutableListOf<Pair<HomeItemFilterBinding, DocType?>>()
    private var filter: DocType? = null
    private var titleActionCount = 0

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
        setupDocumentList()
        setupEmptyState()
        binding.fabScan.setOnClickListener { onScanClick?.invoke() }
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
        binding.titleBar.title = buildTitle()
        addTitleAction(R.mipmap.ic_share_red, R.string.home_share, null) { onShareClick?.invoke() }
        addTitleAction(R.mipmap.ic_print, R.string.home_print, null) { onPrintClick?.invoke() }
        addTitleAction(R.mipmap.ic_menu, R.string.home_more, R.color.brand_primary_dark) {
            onMoreClick?.invoke()
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

    /**
     * Adds one of the three icons on the right hand side of the title bar.
     *
     * The icons sit 40dp apart, so every icon but the first one gets a 16dp
     * gap in front of it.
     */
    private fun addTitleAction(
        iconRes: Int,
        descriptionRes: Int,
        tintRes: Int?,
        onClick: () -> Unit,
    ) {
        val context = requireContext()
        val icon = ImageView(context)
        icon.layoutParams = FrameLayout.LayoutParams(dp(ACTION_SIZE), dp(ACTION_SIZE)).apply {
            marginStart = dp(if (titleActionCount == 0) 0 else ACTION_GAP)
        }
        icon.setImageResource(iconRes)
        icon.contentDescription = getString(descriptionRes)
        tintRes?.let {
            ImageViewCompat.setImageTintList(
                icon,
                ColorStateList.valueOf(ContextCompat.getColor(context, it)),
            )
        }
        icon.setOnClickListener { onClick() }
        titleActionCount++
        binding.titleBar.addAction(icon)
    }

    // ---------------------------------------------------------------- quick actions

    private fun setupQuickActions() {
        quickActionAdapter = HomeQuickActionAdapter { action -> onQuickAction?.invoke(action) }
        binding.quickActions.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.quickActions.adapter = quickActionAdapter
        binding.quickActions.itemAnimator = null
        binding.quickActions.addItemDecoration(GapDecoration(dp(QUICK_ACTION_GAP)))
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
        filterSegments.forEach { (segment, type) ->
            segment.filterLabel.setText(type?.labelRes ?: R.string.all)
            segment.root.setOnClickListener { selectFilter(type) }
        }
        applyFilterState()
    }

    private fun selectFilter(type: DocType?) {
        if (filter == type) return
        filter = type
        prefs.fileFilter = type
        Logger.d(TAG, "file type filter changed to ${type?.name ?: "ALL"}")
        applyFilterState()
        refreshDocuments()
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

    // ---------------------------------------------------------------- document list

    private fun setupDocumentList() {
        documentAdapter = HomeDocumentAdapter(
            onOpen = { document -> onOpenDocument?.invoke(document) },
            onMenu = { document -> onDocumentMenu?.invoke(document) },
        )
        binding.documentList.layoutManager = LinearLayoutManager(requireContext())
        binding.documentList.adapter = documentAdapter
        binding.documentList.setHasFixedSize(true)
        binding.documentList.addItemDecoration(FileListItemDecoration(dp(ROW_GAP)))
    }

    private fun refreshDocuments() {
        if (_binding == null || !::documentAdapter.isInitialized) return
        val documents = DocumentRepository.get(requireContext())
            .all()
            .filter { filter == null || it.type == filter }
        documentAdapter.submitList(documents)
        val empty = documents.isEmpty()
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.documentList.visibility = if (empty) View.GONE else View.VISIBLE
    }

    // ---------------------------------------------------------------- empty state

    private fun setupEmptyState() {
        binding.emptyState.title = getString(R.string.home_empty_title)
        binding.emptyState.hint = getString(R.string.home_empty_hint)
        binding.emptyState.actionText = getString(R.string.home_empty_action)
        binding.emptyState.illustrationDrawable =
            ContextCompat.getDrawable(requireContext(), R.mipmap.bg_file_access)
        binding.emptyState.onActionClick = { openSettings() }
    }

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
        private const val ROW_GAP = 12

        /** The file types the filter strip can show. */
        private val FILTER_TYPES = listOf(
            DocType.PDF,
            DocType.WORD,
            DocType.EXCEL,
            DocType.PPT,
        )
    }
}
