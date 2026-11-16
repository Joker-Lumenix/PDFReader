package com.peng.pdfreader.feature.preview.fragment

import android.content.Context
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.text.format.Formatter
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.office.OoxmlPackage
import com.peng.pdfreader.appcore.office.docx.DocxDocument
import com.peng.pdfreader.appcore.office.docx.DocxParser
import com.peng.pdfreader.appcore.office.pptx.PptxDeck
import com.peng.pdfreader.appcore.office.pptx.PptxParser
import com.peng.pdfreader.appcore.office.xlsx.XlsxParser
import com.peng.pdfreader.appcore.office.xlsx.XlsxWorkbook
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.appcore.widget.DocxPreviewView
import com.peng.pdfreader.appcore.widget.PptxPreviewView
import com.peng.pdfreader.appcore.widget.XlsxPreviewView
import com.peng.pdfreader.base.BaseBackHandler
import com.peng.pdfreader.base.BaseFragment
import com.peng.pdfreader.data.model.DocType
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.databinding.FragmentOfficePreviewBinding
import com.peng.pdfreader.feature.files.print.DocumentPrintAdapter
import com.peng.pdfreader.feature.preview.dialog.MoreActionsSheet
import com.peng.pdfreader.feature.preview.dialog.RenameDialog
import com.peng.pdfreader.feature.preview.util.FileSharing
import com.peng.pdfreader.feature.preview.util.PreviewTitleActions
import com.peng.pdfreader.feature.tools.activity.CompressActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The offline Office preview.
 *
 * The document is parsed by the OOXML parsers of `:appcore` on a background
 * thread and rendered by the format specific preview view.
 */
class OfficePreviewFragment : BaseFragment(), BaseBackHandler {

    private var _binding: FragmentOfficePreviewBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: DocumentRepository
    private var document: Document? = null

    /** The sheet, slide or page that is on screen. */
    private var currentIndex = 0

    private sealed interface Parsed {
        class Docx(val document: DocxDocument) : Parsed
        class Pptx(val deck: PptxDeck) : Parsed
        class Xlsx(val workbook: XlsxWorkbook) : Parsed
        class Text(val content: String) : Parsed
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentOfficePreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = DocumentRepository.get(requireContext())

        val doc = repository.byId(requireArguments().getString(ARG_DOC_ID).orEmpty())
        if (doc == null) {
            requireActivity().finish()
            return
        }
        document = doc

        binding.titleBar.title = doc.name
        // The viewer shows the name of the file with 15sp / medium.
        binding.titleBar.setTitleTypography(15f, true)
        binding.titleBar.showBack = true
        binding.titleBar.onBackClick = {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.titleBar.addAction(
            PreviewTitleActions.build(
                requireContext(),
                onRename = { showRenameDialog() },
                onShare = { share() },
                onMore = { showMoreActions() },
            ),
        )

        binding.ivFavorite.setImageResource(
            if (doc.isFavorite) R.mipmap.ic_star_select else R.mipmap.ic_star_unselect,
        )
        binding.ivFavorite.setOnClickListener { toggleFavorite() }
        binding.tvFavorite.setOnClickListener { toggleFavorite() }
        binding.ivBookmark.setOnClickListener { addBookmark() }
        binding.ivOpenWith.setOnClickListener { openWith() }
        binding.tvOpenWith.setOnClickListener { openWith() }
        binding.ivShare.setOnClickListener { share() }
        binding.tvShare.setOnClickListener { share() }

        showLoading()
        loadDocument()
    }

    // ------------------------------------------------------------------ actions

    private fun toggleFavorite() {
        val doc = document ?: return
        repository.toggleFavorite(doc)
        val updated = repository.byId(doc.id) ?: return
        document = updated
        binding.ivFavorite.setImageResource(
            if (updated.isFavorite) R.mipmap.ic_star_select else R.mipmap.ic_star_unselect,
        )
    }

    private fun share() {
        val doc = document ?: return
        FileSharing.share(requireContext(), repository.fileOf(doc), FileSharing.mimeTypeOf(doc.name))
    }

    private fun openWith() {
        val doc = document ?: return
        val opened = FileSharing.openWith(
            requireContext(),
            repository.fileOf(doc),
            FileSharing.mimeTypeOf(doc.name),
        )
        if (!opened) {
            Toast.makeText(requireContext(), R.string.no_permissions, Toast.LENGTH_SHORT).show()
        }
    }

    private fun addBookmark() {
        val doc = document ?: return
        repository.addBookmark(
            doc.id,
            currentIndex + 1,
            getString(R.string.preview_cd_page, currentIndex + 1),
        )
        Toast.makeText(requireContext(), R.string.preview_bookmark_added, Toast.LENGTH_SHORT).show()
    }

    private fun showRenameDialog() {
        val doc = document ?: return
        val dialog = RenameDialog.create(doc.name)
        dialog.onNameEntered = { name ->
            if (name.isNotEmpty()) {
                val extension = doc.name.substringAfterLast('.', "")
                val safeName = if (extension.isEmpty() || name.contains('.')) name else "$name.$extension"
                repository.rename(doc, safeName)
                val updated = repository.byId(doc.id)
                if (updated != null) {
                    document = updated
                    binding.titleBar.title = updated.name
                }
            }
        }
        dialog.show(parentFragmentManager, "preview_rename")
    }

    private fun showMoreActions() {
        val doc = document
        val sheet = MoreActionsSheet.create(
            actions = listOf(
                MoreActionsSheet.Action(
                    ACTION_RENAME,
                    getString(R.string.rename),
                    R.mipmap.ic_rename_black,
                ),
                MoreActionsSheet.Action(
                    ACTION_OPEN_WITH,
                    getString(R.string.preview_open_with),
                    R.mipmap.ic_open_with,
                ),
                MoreActionsSheet.Action(
                    ACTION_SHARE,
                    getString(R.string.preview_share),
                    R.mipmap.ic_share_square_black,
                ),
                MoreActionsSheet.Action(
                    ACTION_PRINT,
                    getString(R.string.preview_print),
                    R.mipmap.ic_print,
                ),
            ),
            wideAction = MoreActionsSheet.Action(
                ACTION_COMPRESS,
                getString(R.string.compress),
                R.mipmap.ic_compress,
            ),
            documentName = doc?.name.orEmpty(),
            documentType = doc?.let { getString(it.type.labelRes) }.orEmpty(),
            documentMeta = documentMeta(),
            documentLogo = doc?.let {
                ContextCompat.getDrawable(requireContext(), it.type.logoRes)
            },
        )
        sheet.onActionSelected = { id ->
            when (id) {
                ACTION_RENAME -> showRenameDialog()
                ACTION_OPEN_WITH -> openWith()
                ACTION_SHARE -> share()
                ACTION_PRINT -> print()
                ACTION_COMPRESS -> CompressActivity.start(requireContext(), doc?.id)
            }
        }
        sheet.show(parentFragmentManager, "preview_more")
    }

    /**
     * Prints the document.
     *
     * The print pipeline of the app rasterises a PDF, so only the formats the
     * engine can open are sent to the print service.
     */
    private fun print() {
        val doc = document ?: return
        if (doc.type != DocType.PDF && doc.type != DocType.IMAGE) {
            Toast.makeText(
                requireContext(),
                R.string.preview_feature_unavailable,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val manager = requireContext().getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (manager == null) {
            Toast.makeText(
                requireContext(),
                R.string.preview_feature_unavailable,
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .build()
        try {
            manager.print(
                doc.name,
                DocumentPrintAdapter(repository.fileOf(doc), doc.name),
                attributes,
            )
        } catch (error: Exception) {
            Logger.e(TAG, "print of ${doc.id} failed", error)
            Toast.makeText(
                requireContext(),
                R.string.preview_feature_unavailable,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /** The meta line of the more sheet: the date and the size of the file. */
    private fun documentMeta(): String {
        val doc = document ?: return ""
        val date = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(doc.modifiedAt)
        val size = Formatter.formatShortFileSize(requireContext(), doc.sizeBytes)
        return "$date · $size"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onBackHandled(): Boolean = handleBackPressed()

    private fun loadDocument() {
        val doc = document ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val parsed = withContext(Dispatchers.IO) { parse(doc) }
            if (_binding == null) return@launch
            if (parsed == null) {
                showError()
                return@launch
            }
            when (parsed) {
                is Parsed.Docx -> showDocx(parsed.document)
                is Parsed.Pptx -> showPptx(parsed.deck)
                is Parsed.Xlsx -> showXlsx(parsed.workbook)
                is Parsed.Text -> showText(parsed.content)
            }
        }
    }

    private fun parse(doc: Document): Parsed? {
        val file = repository.fileOf(doc)
        if (!file.exists()) return null
        if (doc.type == DocType.TXT) {
            val content = runCatching { file.readText() }.getOrNull() ?: return null
            return Parsed.Text(content)
        }
        val pkg = OoxmlPackage.open(file) ?: return null
        return try {
            when (doc.type) {
                DocType.WORD -> DocxParser.parse(pkg)?.let { Parsed.Docx(it) }
                DocType.PPT -> PptxParser.parse(pkg)?.let { Parsed.Pptx(it) }
                DocType.EXCEL -> XlsxParser.parse(pkg)?.let { Parsed.Xlsx(it) }
                else -> null
            }
        } finally {
            pkg.close()
        }
    }

    private fun showLoading() {
        binding.llLoading.visibility = View.VISIBLE
        binding.flEmpty.visibility = View.GONE
        binding.content.visibility = View.INVISIBLE
    }

    private fun showError() {
        binding.llLoading.visibility = View.GONE
        binding.content.visibility = View.GONE
        binding.flEmpty.visibility = View.VISIBLE
        binding.emptyState.title = getString(R.string.preview_load_failed)
        binding.emptyState.hint = getString(R.string.preview_load_failed_hint)
        binding.emptyState.illustrationDrawable =
            ContextCompat.getDrawable(requireContext(), R.mipmap.bg_empty)
        binding.emptyState.actionText = getString(R.string.preview_retry)
        binding.emptyState.onActionClick = {
            showLoading()
            loadDocument()
        }
    }

    private fun showDocx(document: DocxDocument) {
        val preview = DocxPreviewView(requireContext())
        preview.document = document
        val scroll = ScrollView(requireContext())
        scroll.addView(preview)
        scroll.setOnScrollChangeListener { _, _, scrollY, _, _ -> preview.updateScrollOffset(scrollY) }
        preview.onPageChanged = { page ->
            currentIndex = page
            binding.titleBar.subtitle = getString(R.string.preview_cd_page, page + 1)
        }
        showContent(scroll)
    }

    private fun showPptx(deck: PptxDeck) {
        val preview = PptxPreviewView(requireContext())
        preview.deck = deck
        preview.onSlideChanged = { slide ->
            currentIndex = slide
            binding.titleBar.subtitle = getString(R.string.preview_cd_slide, slide + 1)
        }
        showContent(preview)
    }

    private fun showXlsx(workbook: XlsxWorkbook) {
        val preview = XlsxPreviewView(requireContext())
        preview.workbook = workbook
        preview.onSheetChanged = { sheet ->
            currentIndex = sheet
            binding.titleBar.subtitle = workbook.sheets.getOrNull(sheet)?.name.orEmpty()
        }
        showContent(preview)
    }

    private fun showText(content: String) {
        val text = TextView(requireContext())
        text.text = content
        text.setTextColor(requireContext().getColor(R.color.text_primary))
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        text.setPadding(dp(16), dp(16), dp(16), dp(16))
        val scroll = ScrollView(requireContext())
        scroll.addView(text)
        showContent(scroll)
    }

    private fun showContent(view: View) {
        binding.llLoading.visibility = View.GONE
        binding.flEmpty.visibility = View.GONE
        binding.content.visibility = View.VISIBLE
        binding.content.removeAllViews()
        binding.content.addView(view)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ARG_DOC_ID = "com.peng.pdfreader.extra.DOC_ID"

        private const val ACTION_RENAME = "rename"
        private const val ACTION_OPEN_WITH = "open_with"
        private const val ACTION_SHARE = "share"
        private const val ACTION_PRINT = "print"
        private const val ACTION_COMPRESS = "compress"

        private const val TAG = "OfficePreview"

        /** Creates the preview for the given document. */
        fun newInstance(docId: String): OfficePreviewFragment = OfficePreviewFragment().apply {
            arguments = Bundle().apply { putString(ARG_DOC_ID, docId) }
        }
    }
}
