package com.peng.pdfreader.feature.files.activity

import android.content.Intent
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.appcore.util.SystemBarInsets
import com.peng.pdfreader.appcore.widget.FileItemUiState
import com.peng.pdfreader.appcore.widget.FileListItemDecoration
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.data.prefs.AppPrefs
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.databinding.ActivityFileSearchBinding
import com.peng.pdfreader.feature.files.adapter.FileListAdapter
import com.peng.pdfreader.feature.files.util.DocumentThumbnails
import com.peng.pdfreader.feature.files.util.FileUi
import com.peng.pdfreader.feature.preview.activity.PreviewActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The file search page of the home tab (design: files-搜索).
 *
 * The page is a page of its own: the search field covers the title strip, the
 * hits are listed below it and a hit is painted in the light brand colour.
 */
class FileSearchActivity : BaseActivity() {

    private lateinit var binding: ActivityFileSearchBinding
    private lateinit var repository: DocumentRepository
    private lateinit var adapter: FileListAdapter
    private lateinit var thumbnails: DocumentThumbnails

    /** The keyword of the last query. */
    private var keyword: String? = null

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

        binding.etSearch.requestFocus()
    }

    // ------------------------------------------------------------------ setup

    private fun setupTitleBar() {
        binding.titleBar.title = ""
        binding.titleBar.showBack = true
        binding.titleBar.onBackClick = { finish() }
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
                    PreviewActivity.start(this@FileSearchActivity, document.id)
                }

                override fun onRowMore(document: Document) {
                    // The row menu belongs to the file list of the files tab.
                    Logger.d(TAG, "the row menu of the search page is not available")
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

    private fun setupEmptyState() {
        binding.emptyState.illustrationDrawable =
            ContextCompat.getDrawable(this, R.mipmap.bg_empty)
        binding.emptyState.title = getString(R.string.files_search_empty_title)
        binding.emptyState.hint = getString(R.string.files_search_empty_hint)
    }

    // ------------------------------------------------------------------ data

    private fun load() {
        val query = keyword?.trim()?.takeIf { it.isNotEmpty() }
        val sort = AppPrefs.get(this).fileSort
        lifecycleScope.launch {
            val documents = withContext(Dispatchers.IO) {
                repository.query(null, sort, query)
            }
            render(documents)
        }
    }

    private fun render(documents: List<Document>) {
        // A search hit paints the name in the light brand colour (design:
        // files-搜索).
        val hitColor = if (!keyword.isNullOrBlank()) {
            ContextCompat.getColor(this, R.color.brand_primary_light)
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
                        this,
                        FileUi.typeColor(document.type),
                    ),
                    thumbnail = thumbnails.of(this, document),
                    showThumbnail = true,
                    showType = true,
                    showMore = false,
                    showCheckbox = false,
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

        /** Opens the search page. */
        fun start(context: Context) {
            context.startActivity(Intent(context, FileSearchActivity::class.java))
        }
    }
}
