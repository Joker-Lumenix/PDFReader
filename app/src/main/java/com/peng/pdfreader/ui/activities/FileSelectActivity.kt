package com.peng.pdfreader.ui.activities

import android.content.Context
import android.content.res.ColorStateList
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.widget.FileItemUiState
import com.peng.pdfreader.appcore.widget.FileListItemDecoration
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.base.ShareFileProvider
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.AppPrefs
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.databinding.ActivityFileSelectBinding
import com.peng.pdfreader.ui.adapter.listAdapter.FileListAdapter
import com.peng.pdfreader.ui.activities.CompressActivity
import com.peng.pdfreader.ui.dialogs.DialogDeleteConfirm
import com.peng.pdfreader.ui.dialogs.BottomSheetRename
import com.peng.pdfreader.ui.dialogs.DocumentActionSheet
import com.peng.pdfreader.ui.dialogs.PopWindowFileFilter
import com.peng.pdfreader.ui.uiData.FileActions
import com.peng.pdfreader.utils.DocumentThumbnails
import com.peng.pdfreader.utils.FileUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 文件选择
 */
class FileSelectActivity : BaseActivity() {

    private lateinit var binding: ActivityFileSelectBinding
    private lateinit var repository: DocumentRepository
    private lateinit var prefs: AppPrefs
    private lateinit var adapter: FileListAdapter
    private lateinit var thumbnails: DocumentThumbnails

    /** The ids of the picked files. */
    private val selectedIds = mutableSetOf<String>()

    /** The action that opens the type filter popover. */
    private var filterAction: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = DocumentRepository.get(this)
        prefs = AppPrefs.get(this)
        thumbnails = DocumentThumbnails(repository)

        setupTitleBar()
        setupList()
        setupSelectBar()
        setupEmptyState()
        registerResults()
        load()
    }

    // ------------------------------------------------------------------ setup

    private fun setupTitleBar() {
        binding.ivBack.setOnClickListener {
            finishWithSelection()
        }
        updateTitle()

    }

    private fun setupList() {
        adapter = FileListAdapter(
            object : FileListAdapter.Callbacks {
                override fun onRowClick(document: Document) {
                    toggleSelection(document.id)
                }

                override fun onRowMore(document: Document) {
                    DocumentActionSheet.show(this@FileSelectActivity, supportFragmentManager, document)
                }

                override fun onRowChecked(document: Document, checked: Boolean) {
                    setSelected(document.id, checked)
                }

                override fun onRowLongPressed(document: Document) {
                    setSelected(document.id, true)
                }
            },
        )
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
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
            DialogDeleteConfirm.show(supportFragmentManager, selectedIds.toList())
        }
    }

    private fun setupEmptyState() {
        binding.emptyState.illustrationDrawable =
            ContextCompat.getDrawable(this, R.mipmap.bg_empty)
        binding.emptyState.title = getString(R.string.files_empty_title)
        binding.emptyState.hint = getString(R.string.files_empty_hint)
    }

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
        ) { _, _ ->
            selectedIds.clear()
            load()
        }
    }

    /** Runs the operation that was picked in the more sheet of a row. */
    private fun onFileAction(action: String?, docId: String?) {
        if (action == null || docId == null) return
        when (action) {
            FileActions.RENAME -> BottomSheetRename.show(supportFragmentManager, docId)
            FileActions.DELETE -> DialogDeleteConfirm.show(supportFragmentManager, listOf(docId))
            FileActions.OPEN_WITH -> openWith(docId)
            FileActions.SHARE -> shareDocument(docId)
            FileActions.COMPRESS -> CompressActivity.start(this, docId)
            FileActions.CHANGED -> load()
            else -> toast(getString(R.string.files_feature_unavailable))
        }
    }

    /** Hands the document to another application. */
    private fun openWith(docId: String) {
        val document = repository.byId(docId) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(ShareFileProvider.uriFor(this@FileSelectActivity, repository.fileOf(document)), mimeType(document))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(intent) }
            .onFailure { toast(getString(R.string.files_feature_unavailable)) }
    }

    /** Shares the document with another application. */
    private fun shareDocument(docId: String) {
        val document = repository.byId(docId) ?: return
        val uri = ShareFileProvider.uriFor(this, repository.fileOf(document))
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType(document)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, getString(R.string.files_share))) }
            .onFailure { toast(getString(R.string.files_feature_unavailable)) }
    }

    // ------------------------------------------------------------------ data

    private fun load() {
        val filter = prefs.fileFilter
        val sort = prefs.fileSort
        lifecycleScope.launch {
            val documents = withContext(Dispatchers.IO) {
                // The documents of the device are part of the list as well.
                repository.syncSharedDocuments(applicationContext)
                repository.query(filter, sort, null)
            }
            render(documents)
        }
    }

    private fun render(documents: List<Document>) {
        val rows = documents.map { document ->
            val checked = document.id in selectedIds
            FileListAdapter.Row(
                document = document,
                state = FileItemUiState(
                    name = document.name,
                    meta = FileUi.meta(document),
                    typeLabel = getString(document.type.labelRes),
                    typeColor = ContextCompat.getColor(this, FileUi.typeColor(document.type)),
                    thumbnail = thumbnails.of(this, document),
                    showThumbnail = true,
                    showType = true,
                    // The design shows the "more" affordance on every row that
                    // is not selected, the selection mark takes its place.
                    showMore = !checked,
                    showCheckbox = checked,
                    checkboxAtEnd = true,
                    checked = checked,
                ),
            )
        }
        adapter.submitList(rows)
        val empty = documents.isEmpty()
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.recycler.visibility = if (empty) View.GONE else View.VISIBLE
        updateSelectionUi()
    }

    // ------------------------------------------------------------------ filter

    private fun openFilterMenu() {
        val anchor = filterAction ?: return
        PopWindowFileFilter.show(binding.root, anchor, prefs.fileFilter) { type ->
            prefs.fileFilter = type
            load()
        }
    }

    // ------------------------------------------------------------------ select

    private fun toggleSelection(docId: String) {
        setSelected(docId, docId !in selectedIds)
    }

    private fun setSelected(docId: String, selected: Boolean) {
        if (selected) selectedIds.add(docId) else selectedIds.remove(docId)
        updateSelectionUi()
    }

    /**
     * The action bar follows the design: one picked file offers
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
    }

    private fun updateTitle() {
        binding.tvTitle.text = getString(R.string.selected_count, selectedIds.size)
    }

    // ------------------------------------------------------------------ actions

    private fun renameSelected() {
        val docId = selectedIds.firstOrNull() ?: return
        BottomSheetRename.show(supportFragmentManager, docId)
    }

    private fun mergeSelected() {
        if (selectedIds.size < 2) {
            toast(getString(R.string.files_feature_unavailable))
            return
        }
        MergeActivity.start(this)
    }

    private fun editSelected() {
        val docId = selectedIds.firstOrNull() ?: return
        PreviewActivity.start(this, docId)
    }

    private fun shareSelected() {
        val docs = selectedIds.mapNotNull { repository.byId(it) }
        if (docs.isEmpty()) {
            toast(getString(R.string.files_feature_unavailable))
            return
        }
        val uris = docs.map { ShareFileProvider.uriFor(this, repository.fileOf(it)) }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (docs.size == 1) mimeType(docs.first()) else "*/*"
            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, getString(R.string.files_share))) }
            .onFailure { toast(getString(R.string.files_feature_unavailable)) }
    }

    private fun mimeType(document: Document): String = when (document.type) {
        DocType.PDF -> "application/pdf"
        DocType.WORD -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        DocType.EXCEL -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        DocType.PPT -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        DocType.IMAGE -> "image/*"
        DocType.TXT -> "text/plain"
        DocType.OTHER -> "*/*"
    }

    /** Hands the picked files back to the caller and closes the page. */
    private fun finishWithSelection() {
        if (selectedIds.isNotEmpty()) {
            setResult(
                RESULT_OK,
                Intent().putExtra(EXTRA_SELECTED_IDS, selectedIds.toTypedArray()),
            )
        }
        finish()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ROW_GAP = 12

        /** The ids of the picked files inside the result intent. */
        const val EXTRA_SELECTED_IDS = "com.peng.pdfreader.extra.SELECTED_IDS"

        /** Opens the selection page. */
        fun start(context: Context) {
            context.startActivity(Intent(context, FileSelectActivity::class.java))
        }
    }
}
