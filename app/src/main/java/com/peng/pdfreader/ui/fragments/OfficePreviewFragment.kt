package com.peng.pdfreader.ui.fragments

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
import com.peng.pdfreader.core.office.OoxmlPackage
import com.peng.pdfreader.core.office.edit.DocxEditor
import com.peng.pdfreader.core.office.edit.PptxEditor
import com.peng.pdfreader.core.office.edit.XlsxEditor
import com.peng.pdfreader.core.office.legacy.LegacyDocument
import com.peng.pdfreader.core.office.legacy.LegacyPresentation
import com.peng.pdfreader.core.office.legacy.LegacyWorkbook
import com.peng.pdfreader.core.office.docx.DocxBlock
import com.peng.pdfreader.core.office.docx.DocxDocument
import com.peng.pdfreader.core.office.docx.DocxParser
import com.peng.pdfreader.core.office.pptx.PptxParser
import com.peng.pdfreader.core.office.pptx.PptxShape
import com.peng.pdfreader.core.office.xlsx.XlsxParser
import com.peng.pdfreader.core.util.Logger
import com.peng.pdfreader.core.widget.DocxPreviewView
import com.peng.pdfreader.core.widget.PptxPreviewView
import com.peng.pdfreader.core.widget.XlsxPreviewView
import com.peng.pdfreader.base.BaseBackHandler
import com.peng.pdfreader.base.BaseFragment
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.permission.DefaultAppPrompt
import com.peng.pdfreader.permission.EffectiveReadTracker
import com.peng.pdfreader.databinding.FragmentOfficePreviewBinding
import com.peng.pdfreader.ui.adapter.listAdapter.DocumentPrintAdapter
import com.peng.pdfreader.ui.dialogs.BottomSheetMoreActionNoPdf
import com.peng.pdfreader.ui.dialogs.DialogRename
import com.peng.pdfreader.ui.dialogs.DialogTextEdit
import com.peng.pdfreader.core.office.OfficeText
import com.peng.pdfreader.utils.FileSharing
import com.peng.pdfreader.utils.OfficePrinting
import com.peng.pdfreader.utils.PreviewTitleActions
import java.io.File
import com.peng.pdfreader.ui.activities.CompressActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The offline Office preview.
 *
 * The document is parsed by the OOXML parsers of the core package on a background
 * thread and rendered by the format specific preview view.
 */
class OfficePreviewFragment : BaseFragment(), BaseBackHandler {

    private var _binding: FragmentOfficePreviewBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: DocumentRepository
    private var document: Document? = null

    /** The sheet, slide or page that is on screen. */
    private var currentIndex = 0

    /**
     * The sheet, slide or page that is shown again after a change.
     *
     * The document is read again when a change was written into it, the
     * reader stays on the place the user was on.
     */
    private var restoreIndex = 0

    /**
     * The parts of the sheets of a workbook.
     *
     * The editor needs the part of the sheet to write a changed cell back
     * into the file.
     */
    private var sheetParts: List<String> = emptyList()

    /**
     * The parts of the slides of a presentation.
     *
     * The editor needs the part of the slide to write a changed text back
     * into the file.
     */
    private var slideParts: List<String> = emptyList()

    /**
     * Counts the reading of the document.
     *
     * The prompt of the default reader shows up after an effective read.
     */
    private val readTracker = EffectiveReadTracker(onEffectiveRead = { onEffectiveRead() })

    /** The content of a Word document: the document itself or its text. */
    private sealed interface WordContent {
        class Document(val value: DocxDocument) : WordContent
        class Text(val value: String) : WordContent
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

        binding.tvTitle.text = doc.name
        binding.ivBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.ivRename.setOnClickListener {
            showRenameDialog()
        }

        binding.llShare.setOnClickListener {
            share()
        }

        binding.ivMore.setOnClickListener {
            showMoreActions()
        }

        binding.llOpenWith.setOnClickListener {
            openWith()
        }
        binding.llCollect.setOnClickListener {
            toggleFavorite()
        }

        showLoading()
        loadDocument()
        // The reading is checked every few seconds: the prompt of the default
        // reader shows up while the user reads, not only when the document is
        // closed.
        viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(EffectiveReadTracker.CHECK_INTERVAL_MS)
                readTracker.check()
            }
        }
    }

    private fun toggleFavorite() {
        val doc = document ?: return
        repository.toggleFavorite(doc)
        val updated = repository.byId(doc.id) ?: return
        document = updated
        binding.ivCollect.setImageResource(
            if (updated.isFavorite) R.mipmap.ic_collect_select else R.mipmap.ic_collect_unselect,
        )
    }

    private fun share() {
        val doc = document ?: return
        val mime = FileSharing.mimeTypeOf(doc.name) ?: return
        FileSharing.share(requireContext(), repository.fileOf(doc), mime)
    }

    private fun openWith() {
        val doc = document ?: return
        val mime = FileSharing.mimeTypeOf(doc.name) ?: return
        val opened = FileSharing.openWith(
            requireContext(),
            repository.fileOf(doc),
            mime,
        )
        if (!opened) {
            Toast.makeText(requireContext(), R.string.no_permissions, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 添加书签
     */
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
        val dialog = DialogRename.create(doc.name)
        dialog.onNameEntered = { name ->
            if (name.isNotEmpty()) {
                val extension = doc.name.substringAfterLast('.', "")
                val safeName = if (extension.isEmpty() || name.contains('.')) name else "$name.$extension"
                repository.rename(doc, safeName)
                val updated = repository.byId(doc.id)
                if (updated != null) {
                    document = updated
                    binding.tvTitle.text = updated.name
                }
            }
        }
        dialog.show(parentFragmentManager, "preview_rename")
    }

    private fun showMoreActions() {
        val doc = document
        val sheet = BottomSheetMoreActionNoPdf.create(
            actions = listOf(
                BottomSheetMoreActionNoPdf.Action(
                    ACTION_RENAME,
                    getString(R.string.rename),
                    R.mipmap.ic_rename_red,
                ),
                BottomSheetMoreActionNoPdf.Action(
                    ACTION_OPEN_WITH,
                    getString(R.string.preview_open_with),
                    R.mipmap.ic_open_with,
                ),
                BottomSheetMoreActionNoPdf.Action(
                    ACTION_SHARE,
                    getString(R.string.preview_share),
                    R.mipmap.ic_share_red,
                ),
                BottomSheetMoreActionNoPdf.Action(
                    ACTION_PRINT,
                    getString(R.string.preview_print),
                    R.mipmap.ic_print,
                ),
            ),
            wideAction = BottomSheetMoreActionNoPdf.Action(
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
        // The document id is needed by the favourite button of the sheet.
        sheet.documentId = doc?.id.orEmpty()
        sheet.show(parentFragmentManager, "preview_more")
    }

    /**
     * Prints the document.
     *
     * The print pipeline of the app rasterises a PDF. The Word / Excel /
     * PowerPoint documents are printed with their text.
     */
    private fun print() {
        val doc = document ?: return
        if (doc.type == DocType.WORD || doc.type == DocType.EXCEL || doc.type == DocType.PPT) {
            val printed = OfficePrinting.print(
                requireContext(),
                repository.fileOf(doc),
                doc.name,
            )
            if (!printed) {
                Toast.makeText(
                    requireContext(),
                    R.string.preview_feature_unavailable,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            return
        }
        if (doc.type != DocType.PDF) {
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

    override fun onResume() {
        super.onResume()
        readTracker.onResumed()
    }

    override fun onPause() {
        super.onPause()
        readTracker.onPaused()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        readTracker.onClosed()
        _binding = null
    }

    /**
     * The user read the document long enough for the prompt of the default
     * reader to be due.
     */
    private fun onEffectiveRead() {
        val doc = document ?: return
        if (!isAdded) return
        DefaultAppPrompt.onEffectiveRead(requireContext(), childFragmentManager, doc.type)
    }

    override fun onBackHandled(): Boolean = handleBackPressed()

    private fun loadDocument() {
        val doc = document ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            when (doc.type) {
                DocType.WORD -> loadWord(doc)
                DocType.PPT -> loadPresentation(doc)
                DocType.EXCEL -> loadWorkbook(doc)
                DocType.PDF -> showError()
            }
        }
    }

    /**
     * Reads a Word document.
     *
     * The document is parsed on a background thread, the pages of the
     * document are laid out afterwards, page by page.
     */
    private suspend fun loadWord(doc: Document) {
        val file = repository.fileOf(doc)
        if (file.exists() && !file.name.endsWith(".docx", true)) {
            // The legacy binary format of the documents.
            showLegacyDocument(doc, file)
            return
        }
        val parsed = withContext(Dispatchers.IO) {
            try {
                parseWord(doc)
            } catch (error: OutOfMemoryError) {
                // A big document does not fit into the memory of the phone.
                Logger.e(TAG, "the document ${doc.name} is too big to be previewed")
                null
            }
        }
        if (_binding == null) return
        if (parsed == null) {
            showError()
            return
        }
        when (parsed) {
            is WordContent.Document -> showDocx(parsed.value)
            is WordContent.Text -> showText(parsed.value)
        }
    }

    /**
     * Reads a presentation.
     *
     * The slides are read one after the other, the first one is on the screen
     * while the rest of the deck is still read.
     */
    private suspend fun loadPresentation(doc: Document) {
        val file = repository.fileOf(doc)
        if (!file.exists()) {
            showError()
            return
        }
        if (!file.name.endsWith(".pptx", true)) {
            // The legacy binary format of the presentations.
            showLegacyPresentation(doc, file)
            return
        }
        val pkg = withContext(Dispatchers.IO) { OoxmlPackage.open(file) }
        if (pkg == null) {
            showError()
            return
        }
        val reader = withContext(Dispatchers.IO) { PptxParser.Reader(pkg) }
        slideParts = (0 until reader.slideCount).mapNotNull { reader.slidePart(it) }
        val preview = PptxPreviewView(requireContext())
        preview.setDeckSize(reader.widthDp, reader.heightDp)
        preview.slideCount = reader.slideCount
        preview.onSlideChanged = { slide ->
            currentIndex = slide
            readTracker.onPage(slide)
        }
        preview.onShapeLongPressed = { shape -> editShape(shape) }
        showContent(preview)
        preview.scrollToSlide(restoreIndex)
        try {
            for (index in 0 until reader.slideCount) {
                val slide = withContext(Dispatchers.IO) { reader.readSlide(index) } ?: continue
                if (_binding == null) return
                preview.appendSlide(slide)
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { pkg.close() }
        }
    }

    /**
     * Reads a spreadsheet.
     *
     * The sheets are read one after the other, the first one is on the screen
     * while the rest of the workbook is still read.
     */
    private suspend fun loadWorkbook(doc: Document) {
        val file = repository.fileOf(doc)
        if (!file.exists()) {
            showError()
            return
        }
        if (!file.name.endsWith(".xlsx", true)) {
            // The legacy binary format of the spreadsheets.
            showLegacyWorkbook(doc, file)
            return
        }
        val pkg = withContext(Dispatchers.IO) { OoxmlPackage.open(file) }
        if (pkg == null) {
            showError()
            return
        }
        val reader = withContext(Dispatchers.IO) { XlsxParser.Reader(pkg) }
        sheetParts = (0 until reader.sheetCount).mapNotNull { reader.sheetPart(it) }
        val preview = XlsxPreviewView(requireContext())
        preview.sheetCount = reader.sheetCount
        preview.sheetIndex = reader.activeSheetIndex
        for (index in 0 until reader.sheetCount) {
            preview.setSheetName(index, reader.sheetName(index))
        }
        preview.onSheetChanged = { sheet ->
            currentIndex = sheet
            readTracker.onPage(sheet)
        }
        preview.onCellLongPressed = { row, column -> editCell(row, column) }
        showContent(preview)
        preview.sheetIndex = restoreIndex.coerceIn(0, (reader.sheetCount - 1).coerceAtLeast(0))
        try {
            for (index in 0 until reader.sheetCount) {
                val sheet = withContext(Dispatchers.IO) { reader.readSheet(index) } ?: continue
                if (_binding == null) return
                preview.setSheet(index, sheet)
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { pkg.close() }
        }
    }

    /**
     * Shows a document in the legacy binary format of Word.
     *
     * The format is the compound file of Office 97, the reader of Apache POI
     * opens it.
     */
    private suspend fun showLegacyDocument(doc: Document, file: File) {
        val parsed = withContext(Dispatchers.IO) { LegacyDocument.read(file) }
        if (parsed == null) {
            showError()
            return
        }
        val preview = DocxPreviewView(requireContext())
        preview.document = parsed
        preview.onPageChanged = { page ->
            currentIndex = page
            readTracker.onPage(page)
        }
        preview.onLayoutFailed = { toast(R.string.preview_document_truncated) }
        showContent(preview)
        // A document that is too big for the phone is cut off.
        if (parsed.truncated) toast(R.string.preview_document_truncated)
    }

    /**
     * Shows a spreadsheet in the legacy binary format of Excel.
     */
    private suspend fun showLegacyWorkbook(doc: Document, file: File) {
        val workbook = withContext(Dispatchers.IO) { LegacyWorkbook.read(file) }
        if (workbook == null) {
            showError()
            return
        }
        val preview = XlsxPreviewView(requireContext())
        preview.sheetCount = workbook.sheets.size
        preview.sheetIndex = workbook.activeSheetIndex
        for (index in workbook.sheets.indices) {
            preview.setSheetName(index, workbook.sheets[index].name)
        }
        preview.onSheetChanged = { sheet ->
            currentIndex = sheet
            readTracker.onPage(sheet)
        }
        showContent(preview)
        for (index in workbook.sheets.indices) {
            preview.setSheet(index, workbook.sheets[index])
        }
    }

    /**
     * Shows a presentation in the legacy binary format of PowerPoint.
     */
    private suspend fun showLegacyPresentation(doc: Document, file: File) {
        val deck = withContext(Dispatchers.IO) { LegacyPresentation.read(file) }
        if (deck == null) {
            showError()
            return
        }
        val preview = PptxPreviewView(requireContext())
        preview.setDeckSize(deck.widthDp, deck.heightDp)
        preview.slideCount = deck.slides.size
        preview.onSlideChanged = { slide ->
            currentIndex = slide
            readTracker.onPage(slide)
        }
        showContent(preview)
        for (slide in deck.slides) {
            preview.appendSlide(slide)
        }
    }

    private fun parseWord(doc: Document): WordContent? {
        val file = repository.fileOf(doc)
        if (!file.exists()) return null
        return try {
            val pkg = OoxmlPackage.open(file) ?: return textFallback(doc, file)
            try {
                DocxParser.parse(pkg)?.let { WordContent.Document(it) } ?: textFallback(doc, file)
            } finally {
                pkg.close()
            }
        } catch (error: OutOfMemoryError) {
            // The tree of a big document does not fit into the memory of the
            // phone, the text of the document is shown instead.
            Logger.e(TAG, "the document ${doc.name} is too big for the preview")
            textFallback(doc, file)
        } catch (error: Exception) {
            Logger.e(TAG, "the document ${doc.name} could not be parsed", error)
            textFallback(doc, file)
        }
    }

    /**
     * The fallback of a document that does not fit into the memory.
     *
     * The text is read with a streaming parser, so it needs only a little
     * memory.
     */
    private fun textFallback(doc: Document, file: File): WordContent? {
        val lines = OfficeText.lines(file)
        if (lines.isEmpty()) return null
        return WordContent.Text(lines.joinToString("\n"))
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
        preview.onPageChanged = { page ->
            currentIndex = page
            readTracker.onPage(page)
        }
        preview.onParagraphLongPressed = { paragraph -> editParagraph(paragraph) }
        preview.onLayoutFailed = { toast(R.string.preview_document_truncated) }
        showContent(preview)
        preview.scrollToPage(restoreIndex)
        // A document that is too big for the phone is cut off, the reader
        // tells the user that the document is not complete.
        if (document.truncated) toast(R.string.preview_document_truncated)
    }

    // ------------------------------------------------------------------ edits

    /**
     * Edits the text of a paragraph of a Word document.
     */
    private fun editParagraph(paragraph: DocxBlock.Paragraph) {
        if (paragraph.sourceIndex < 0) return
        val text = paragraph.runs.joinToString("") { it.text }
        openEditor(text) { value -> saveEdit { DocxEditor.setParagraphText(fileOfDocument(), paragraph.sourceIndex, value) } }
    }

    /**
     * Edits the value of a cell of a spreadsheet.
     */
    private fun editCell(row: Int, column: Int) {
        val part = sheetParts.getOrNull(currentIndex) ?: return
        val value = currentCellValue(row, column) ?: return
        openEditor(value) { text -> saveEdit { XlsxEditor.setCellValue(fileOfDocument(), part, row, column, text) } }
    }

    /**
     * Edits the text of a text box of a slide.
     */
    private fun editShape(shape: PptxShape.TextBox) {
        if (shape.sourceIndex < 0) return
        val part = slideParts.getOrNull(currentIndex) ?: return
        val text = shape.paragraphs.joinToString("\n") { it.text }
        openEditor(text) { value -> saveEdit { PptxEditor.setShapeText(fileOfDocument(), part, shape.sourceIndex, value) } }
    }

    /**
     * Opens the editor of a text.
     *
     * The editor is only used for the formats of the office software - a
     * document of the legacy binary format cannot be written back.
     */
    private fun openEditor(text: String, onSave: (String) -> Unit) {
        val doc = document ?: return
        if (!doc.name.endsWith(".docx", true) &&
            !doc.name.endsWith(".xlsx", true) &&
            !doc.name.endsWith(".pptx", true)
        ) {
            toast(R.string.preview_feature_unavailable)
            return
        }
        val file = repository.fileOf(doc)
        if (!file.canWrite()) {
            toast(R.string.preview_edit_readonly)
            return
        }
        val dialog = DialogTextEdit.create(text)
        dialog.onTextEntered = edited@{ value ->
            if (value == null) return@edited
            // A text that was not changed is not written back: the file of a
            // cell with a number would carry the text of the number.
            if (value == text) return@edited
            onSave(value)
        }
        dialog.show(parentFragmentManager, "preview_edit_text")
    }

    /**
     * Writes a change into the file of the document.
     *
     * The document is read again afterwards, so the screen shows the file and
     * not the model of the reader.
     */
    private fun saveEdit(write: () -> Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { write() }.getOrDefault(false)
            }
            if (!ok) {
                toast(R.string.preview_edit_failed)
                return@launch
            }
            toast(R.string.preview_edit_saved)
            refreshDocument()
        }
    }

    /** The file of the document that is on the screen. */
    private fun fileOfDocument(): File {
        val doc = document ?: return File(".")
        return repository.fileOf(doc)
    }

    /** The value of a cell of the sheet that is on the screen. */
    private fun currentCellValue(row: Int, column: Int): String? {
        val preview = binding.content.getChildAt(0) as? XlsxPreviewView ?: return null
        return preview.valueOf(row, column)
    }

    /** Reads the document again, so the change is on the screen. */
    private fun refreshDocument() {
        val doc = document ?: return
        restoreIndex = currentIndex
        val file = repository.fileOf(doc)
        repository.update(
            doc.copy(
                sizeBytes = file.length(),
                modifiedAt = System.currentTimeMillis(),
            ),
        )
        document = repository.byId(doc.id) ?: doc
        showLoading()
        loadDocument()
    }

    /** Shows a short message. */
    private fun toast(message: Int) {
        if (_binding == null) return
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
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
        // The document is on the screen, the reading of the user starts now.
        readTracker.onOpened(0)
        binding.llLoading.visibility = View.GONE
        binding.flEmpty.visibility = View.GONE
        binding.content.visibility = View.VISIBLE
        binding.content.removeAllViews()
        binding.content.addView(view)
        showEditHint()
    }

    /**
     * Hints that the text of the document can be changed.
     *
     * The hint is shown once for the whole app, a document that cannot be
     * edited never shows it.
     */
    private fun showEditHint() {
        if (editHintShown) return
        val doc = document ?: return
        val editable = doc.name.endsWith(".docx", true) ||
            doc.name.endsWith(".xlsx", true) ||
            doc.name.endsWith(".pptx", true)
        if (!editable) return
        editHintShown = true
        toast(R.string.preview_edit_longpress)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ARG_DOC_ID = "com.peng.pdfreader.extra.DOC_ID"

        /**
         * Whether the hint of the editor was shown already.
         *
         * The hint is shown once for the whole app, a reader does not need it
         * a second time.
         */
        private var editHintShown = false

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
