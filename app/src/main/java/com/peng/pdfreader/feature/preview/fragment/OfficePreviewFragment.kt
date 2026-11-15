package com.peng.pdfreader.feature.preview.fragment

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
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
import com.peng.pdfreader.appcore.widget.DocxPreviewView
import com.peng.pdfreader.appcore.widget.PptxPreviewView
import com.peng.pdfreader.appcore.widget.XlsxPreviewView
import com.peng.pdfreader.base.BaseBackHandler
import com.peng.pdfreader.base.BaseFragment
import com.peng.pdfreader.data.model.DocType
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.databinding.FragmentOfficePreviewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
        binding.titleBar.showBack = true
        binding.titleBar.onBackClick = {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        showLoading()
        loadDocument()
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
            binding.titleBar.subtitle = getString(R.string.preview_cd_page, page + 1)
        }
        showContent(scroll)
    }

    private fun showPptx(deck: PptxDeck) {
        val preview = PptxPreviewView(requireContext())
        preview.deck = deck
        preview.onSlideChanged = { slide ->
            binding.titleBar.subtitle = getString(R.string.preview_cd_slide, slide + 1)
        }
        showContent(preview)
    }

    private fun showXlsx(workbook: XlsxWorkbook) {
        val preview = XlsxPreviewView(requireContext())
        preview.workbook = workbook
        preview.onSheetChanged = { sheet ->
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

        /** Creates the preview for the given document. */
        fun newInstance(docId: String): OfficePreviewFragment = OfficePreviewFragment().apply {
            arguments = Bundle().apply { putString(ARG_DOC_ID, docId) }
        }
    }
}
