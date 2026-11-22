package com.peng.pdfreader.ui.fragments

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.peng.pdfreader.R
import com.peng.pdfreader.core.pdf.PageInk
import com.peng.pdfreader.core.pdf.PdfEngine
import com.peng.pdfreader.core.pdf.PdfError
import com.peng.pdfreader.core.pdf.PdfOpen
import com.peng.pdfreader.core.pdf.PdfVault
import com.peng.pdfreader.core.util.SystemBarInsets
import com.peng.pdfreader.core.widget.ColorPaletteView
import com.peng.pdfreader.core.widget.IconFilters
import com.peng.pdfreader.core.widget.InkStroke
import com.peng.pdfreader.core.widget.PdfPageView
import com.peng.pdfreader.core.widget.ReadingMode
import com.peng.pdfreader.base.BaseBackHandler
import com.peng.pdfreader.base.BaseFragment
import com.peng.pdfreader.data.DocType
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.data.AppPrefs
import com.peng.pdfreader.data.Annotation
import com.peng.pdfreader.data.AnnotationType
import com.peng.pdfreader.data.DocumentRepository
import com.peng.pdfreader.permission.DefaultAppPrompt
import com.peng.pdfreader.permission.EffectiveReadTracker
import com.peng.pdfreader.databinding.FragmentPdfPreviewBinding
import com.peng.pdfreader.ui.dialogs.BottomSheetPassword
import com.peng.pdfreader.ui.dialogs.BottomSheetOutline
import com.peng.pdfreader.ui.dialogs.BottomSheetSearch
import com.peng.pdfreader.ui.activities.SignatureActivity
import com.peng.pdfreader.ui.adapter.listAdapter.PdfPagerAdapter
import com.peng.pdfreader.ui.uiData.AnnotationMarks
import com.peng.pdfreader.ui.uiData.AnnotationPayload
import com.peng.pdfreader.ui.uiData.FileActions
import com.peng.pdfreader.ui.dialogs.BottomSheetRename
import com.peng.pdfreader.ui.dialogs.DocumentActionSheet
import com.peng.pdfreader.ui.dialogs.DialogDeleteConfirm
import com.peng.pdfreader.ui.dialogs.DialogFailure
import com.peng.pdfreader.ui.dialogs.DialogNote
import com.peng.pdfreader.utils.FileSharing
import com.peng.pdfreader.utils.DocumentActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * The reader of the app.
 *
 * The signature and the free hand follow the design of the bottom bar, the
 * strip above it carries the marks of the reader: highlight, underline, strike
 * through, free hand, note and signature.
 */
class PdfPreviewFragment : BaseFragment(), BaseBackHandler {

    private var _binding: FragmentPdfPreviewBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: DocumentRepository
    private lateinit var prefs: AppPrefs

    private var document: Document? = null
    private var pagerAdapter: PdfPagerAdapter? = null
    private var pageCount = 0
    private var currentPage = 0

    /** Whether the pages are scrolled vertically instead of turned. */
    private var verticalScroll = false
    private var activeTool: AnnotationType? = null
    private var bottomSheetPassword: BottomSheetPassword? = null
    private var renderFailureReported = false
    private val annotationColour = Color.parseColor("#fb4141")

    /** Whether the pages take the ink of the finger. */
    private var drawMode = false

    /** The free hand ink of the reader, page by page. */
    private val inkPages = mutableMapOf<Int, MutableList<InkStroke>>()

    /** The strokes that were taken back by the undo of every page. */
    private val inkRedoPages = mutableMapOf<Int, MutableList<InkStroke>>()

    /** Colour of the next stroke of the drawing. */
    private var drawColour = Color.parseColor("#fb4141")

    /** Width of the next stroke of the drawing, in pixels. */
    private var drawWidth = 0f

    private val signatureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> handleSignatureResult(result.resultCode, result.data) }

    /**
     * Counts the reading of the document.
     *
     * The prompt of the default reader shows up after an effective read.
     */
    private val readTracker = EffectiveReadTracker(onEffectiveRead = { onEffectiveRead() })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPdfPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = DocumentRepository.get(requireContext())
        prefs = AppPrefs.get(requireContext())

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
            showRenameSheet()
        }
        binding.ivMore.setOnClickListener {
            showMoreActions()
        }

        binding.ivScroll.setOnClickListener {
            toggleScrollMode()
        }

        binding.llShare.setOnClickListener {
            share()
        }

        // The add bar of the design offers a new signature.
        binding.addBar.background = GradientDrawable().apply {
            setColor(ADD_BAR_BACKGROUND)
        }
        binding.addBar.setOnClickListener { launchSignatureEditor() }

        // The bar of the editing modes: close, undo, redo and the confirm pill.
        binding.ivClose.setOnClickListener {
            if (drawMode) {
                // The drawing is left, the ink stays on the pages.
                stopDrawing()
            } else {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
        binding.ivUndo.setOnClickListener { undoInk() }
        binding.ivRedo.setOnClickListener { redoInk() }
        binding.flDrawDone.setOnClickListener { saveDrawing() }
        binding.flSignDone.setOnClickListener { saveSignature() }

        binding.llSign.setOnClickListener {
            selectTool(AnnotationType.SIGNATURE)
        }

        binding.llDraw.setOnClickListener {
            selectTool(AnnotationType.DRAW)
        }

        setupAnnotationToolbar()
        setupDrawToolbar()
        applyEditorInsets()

        binding.pager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    currentPage = position
                    readTracker.onPage(position)
                    // The ink of the drawing belongs to the page, so the
                    // history follows the reader.
                    updateDrawButtons()
                }
            },
        )
        registerResults()
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

    /**
     * Listens to the operations of the more sheet of the reader.
     *
     * The sheet is the one of the file list (design: home-more) and the
     * operations are run by the shared runner, so a file behaves the same in
     * the reader and in the list.
     */
    private fun registerResults() {
        childFragmentManager.setFragmentResultListener(
            FileActions.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            val action = bundle.getString(FileActions.KEY_ACTION) ?: return@setFragmentResultListener
            val docId = bundle.getString(FileActions.KEY_DOC_ID) ?: return@setFragmentResultListener
            DocumentActions(this).run(action, docId)
            refreshDocument()
        }
        childFragmentManager.setFragmentResultListener(
            BottomSheetRename.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, _ -> refreshDocument() }
        childFragmentManager.setFragmentResultListener(
            DialogDeleteConfirm.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, _ ->
            // The document is gone, the reader closes itself.
            activity?.finish()
        }
    }

    override fun onResume() {
        super.onResume()
        readTracker.onResumed()
    }

    override fun onPause() {
        super.onPause()
        readTracker.onPaused()
        persistProgress()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        readTracker.onClosed()
        // The engine keeps the parsed document open while it is read, this
        // gives the memory of a big document back.
        pagerAdapter?.release()
        pagerAdapter = null
        _binding = null
    }

    override fun handleBackPressed(): Boolean {
        if (drawMode) {
            // The drawing is left, the ink stays on the pages.
            stopDrawing()
            return true
        }
        // The reader closes itself; a signature that waits for its place is
        // dropped, only the confirm pill of the design writes it into the
        // document.
        return false
    }

    override fun onBackHandled(): Boolean = handleBackPressed()

    // ------------------------------------------------------------------ document

    private fun loadDocument() {
        val doc = document ?: return
        val file = repository.fileOf(doc)
        if (doc.isEncrypted || PdfVault.isEncrypted(file)) {
            requestPassword(doc, file)
            return
        }
        openDocument(doc, file)
    }

    /**
     * Opens the document in the pager.
     *
     * @param startPage the page the reader starts on, the last page of the
     * document when it is negative.
     */
    private fun openDocument(doc: Document, file: File, startPage: Int = -1) {
        viewLifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                PdfEngine.open(file)
            }
            if (_binding == null) return@launch
            if (outcome is PdfOpen.Failed) {
                // The engine reports what went wrong, the design explains it.
                showOpenFailure(doc, outcome.code)
                return@launch
            }
            val count = (outcome as PdfOpen.Opened).pageCount
            pageCount = count.coerceAtLeast(1)
            val adapter = PdfPagerAdapter(
                file,
                pageCount,
                readingMode(),
            )
            adapter.marksProvider = { page ->
                AnnotationMarks.marksOf(repository.annotationsOf(doc.id), page)
            }
            adapter.onRenderFailed = { reportRenderFailure() }
            // The number of the page is painted on it (design: sign-1).
            adapter.showPageBadge = true
            // The old adapter holds the pages of the file that was open before.
            pagerAdapter?.release()
            pagerAdapter = adapter
            binding.pager.adapter = adapter
            val start = if (startPage >= 0) {
                startPage.coerceIn(0, pageCount - 1)
            } else {
                (doc.lastPage - 1).coerceIn(0, pageCount - 1)
            }
            if (start > 0) binding.pager.setCurrentItem(start, false)
            currentPage = start
            // The document is open, the reading of the user starts now.
            readTracker.onOpened(start)
        }
    }

    /**
     * The reader shows the pages one after the other, or it scrolls them
     * vertically.
     *
     * The button of the title bar switches between the two: the pages are
     * turned in the first mode and scrolled in the second one.
     */
    private fun toggleScrollMode() {
        verticalScroll = !verticalScroll
        binding.pager.orientation = if (verticalScroll) {
            ViewPager2.ORIENTATION_VERTICAL
        } else {
            ViewPager2.ORIENTATION_HORIZONTAL
        }
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

    /**
     * Reads the document again.
     *
     * The sheet may have renamed the file or changed its favourite state: the
     * title follows the document and a renamed file is opened again, so the
     * pages keep coming.
     */
    private fun refreshDocument() {
        val doc = document ?: return
        val updated = repository.byId(doc.id) ?: return
        document = updated
        binding.tvTitle.text = updated.name
        if (updated.path == doc.path) return
        val page = currentPage
        openDocument(updated, repository.fileOf(updated), page)
    }

    /**
     * A page could not be shown.
     *
     * The reader stays open, the other pages are still there.
     */
    private fun reportRenderFailure() {
        if (_binding == null) return
        if (renderFailureReported) return
        renderFailureReported = true
        Toast.makeText(requireContext(), R.string.preview_load_failed, Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------------ locked documents

    /**
     * The document cannot be opened.
     *
     * The dialog of the design explains it, the page stays open so the user
     * can go back to the list.
     */
    private fun showOpenFailure(doc: Document, code: String) {
        val message = when (code) {
            PdfError.CODE_ENCRYPTED -> getString(R.string.error_pdf_encrypted)
            PdfError.CODE_UNREADABLE -> getString(R.string.preview_open_failed)
            else -> if (doc.type == DocType.PDF) {
                getString(R.string.preview_open_invalid)
            } else {
                getString(R.string.preview_open_failed)
            }
        }
        DialogFailure.show(childFragmentManager, message)
    }

    /**
     * A locked document is a vault container, the renderer cannot open it.
     * The password sheet asks for the password, the decrypted copy is then
     * rendered from the cache.
     */
    private fun requestPassword(doc: Document, file: File, errorMessage: String? = null) {
        bottomSheetPassword?.dismiss()
        val sheet = BottomSheetPassword.newInstance(BottomSheetPassword.Mode.ENTER)
        bottomSheetPassword = sheet
        errorMessage?.let { message ->
            sheet.showErrorOnShow = message
        }
        childFragmentManager.setFragmentResultListener(
            BottomSheetPassword.RESULT_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            if (bundle.getBoolean(BottomSheetPassword.KEY_CANCELLED)) {
                // The user gave up: the document stays locked, the page of the
                // reader is left.
                leavePage()
                return@setFragmentResultListener
            }
            val password = bundle.getString(BottomSheetPassword.KEY_PASSWORD).orEmpty()
            unlockAndOpen(doc, file, password)
        }
        sheet.show(childFragmentManager, BottomSheetPassword.TAG)
    }

    /** Leaves the page of the reader. */
    private fun leavePage() {
        activity?.finish()
    }

    private fun unlockAndOpen(doc: Document, file: File, password: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val out = File(requireContext().cacheDir, "unlocked/${doc.id}.pdf")
                out.parentFile?.mkdirs()
                PdfVault.unlock(file, out, password)
            }
            result.fold(
                onSuccess = { decrypted ->
                    if (_binding != null) openDocument(doc, decrypted)
                },
                onFailure = { error ->
                    val message = if (error is PdfVault.VaultError.WrongPassword) {
                        getString(R.string.lock_wrong_password)
                    } else {
                        error.message.orEmpty()
                    }
                    requestPassword(doc, file, message)
                },
            )
        }
    }

    private fun persistProgress() {
        val doc = document ?: return
        if (pageCount <= 0) return
        repository.setProgress(doc, currentPage + 1)
    }

    private fun readingMode(): ReadingMode = prefs.readingMode

    // ------------------------------------------------------------------ actions

    private fun share() {
        val doc = document ?: return
        val file = repository.fileOf(doc)
        val mime = FileSharing.mimeTypeOf(doc.name) ?: return
        FileSharing.share(requireContext(), file, mime)
    }

    /**
     * The more sheet of the reader (design: home-more).
     *
     * The reader opens the sheet of the file list: the same operations, the
     * same sheet and the same behaviour, so a file behaves the same wherever
     * it is opened from.
     */
    private fun showMoreActions() {
        val doc = document ?: return
        DocumentActionSheet.show(requireContext(), childFragmentManager, doc)
    }

    /**
     * The rename sheet of the app (design: home-PDF-rename).
     *
     * The reader renames its file with the sheet of the list, so the name is
     * written the same way everywhere.
     */
    private fun showRenameSheet() {
        val doc = document ?: return
        BottomSheetRename.show(childFragmentManager, doc.id)
    }

    private fun closeAnnotationBar() {
        activeTool = null
        pagerAdapter?.applySelection(false, null)
        updateAnnotationToolbar()
    }

    /**
     * The strip of the annotation tools.
     *
     * Only a PDF file carries annotations, so the strip stays away for the
     * other formats.
     */
    private fun setupAnnotationToolbar() {
        val toolbar = binding.annotationToolbar
        toolbar.toolHighlight.setOnClickListener { selectTool(AnnotationType.HIGHLIGHT) }
        toolbar.toolUnderline.setOnClickListener { selectTool(AnnotationType.UNDERLINE) }
        toolbar.toolStrikethrough.setOnClickListener { selectTool(AnnotationType.STRIKETHROUGH) }
        toolbar.toolDraw.setOnClickListener { selectTool(AnnotationType.DRAW) }
        toolbar.toolNote.setOnClickListener { selectTool(AnnotationType.NOTE) }
        toolbar.toolSignature.setOnClickListener { selectTool(AnnotationType.SIGNATURE) }
        toolbar.toolOutline.setOnClickListener { showOutline() }
        toolbar.toolSearch.setOnClickListener { showSearch() }
        toolbar.toolSave.setOnClickListener { saveAnnotationsIntoDocument() }
        toolbar.root.visibility =
            if (document?.type == DocType.PDF) View.VISIBLE else View.GONE
    }

    /**
     * Shows the outline of the document.
     *
     * The outline is read on a background thread, the sheet lists the entries
     * of it.
     */
    private fun showOutline() {
        val doc = document ?: return
        val file = repository.fileOf(doc)
        viewLifecycleOwner.lifecycleScope.launch {
            val outline = withContext(Dispatchers.IO) { PdfEngine.extractOutline(file) }
            if (outline.isEmpty()) {
                Toast.makeText(requireContext(), R.string.no_outline, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val sheet = BottomSheetOutline.create(
                outline.map { it.title },
                outline.map { it.page },
            )
            sheet.onEntrySelected = { index ->
                outline.getOrNull(index)?.let { jumpToPage(it.page) }
            }
            sheet.show(parentFragmentManager, "preview_outline")
        }
    }

    /**
     * Searches the text of the document.
     */
    private fun showSearch() {
        val sheet = BottomSheetSearch.create("")
        sheet.onSearch = { word -> searchDocument(sheet, word) }
        sheet.onPageSelected = { page -> jumpToPage(page) }
        sheet.show(parentFragmentManager, "preview_search")
    }

    /** Reads the hits of a word and shows them in the sheet. */
    private fun searchDocument(sheet: BottomSheetSearch, word: String) {
        val doc = document ?: return
        val file = repository.fileOf(doc)
        viewLifecycleOwner.lifecycleScope.launch {
            val hits = withContext(Dispatchers.IO) { PdfEngine.searchText(file, word) }
            sheet.hits = hits.map { BottomSheetSearch.Hit(it.first, it.second) }
        }
    }

    /**
     * Writes the annotations of the document into the file.
     *
     * The annotations are written into the document, so it carries them when
     * it is opened with another program.
     */
    private fun saveAnnotationsIntoDocument() {
        val doc = document ?: return
        val file = repository.fileOf(doc)
        val annotations = repository.annotationsOf(doc.id)
        if (annotations.isEmpty()) {
            Toast.makeText(requireContext(), R.string.preview_feature_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val target = File(
                File(requireContext().cacheDir, "annotated").apply { mkdirs() },
                "${doc.id}.pdf",
            )
            val written = withContext(Dispatchers.IO) {
                PdfEngine.writeAnnotations(
                    file,
                    target,
                    AnnotationMarks.pdfAnnotationsOf(annotations),
                )
            }
            if (!written) {
                Toast.makeText(requireContext(), R.string.preview_edit_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            withContext(Dispatchers.IO) {
                // The reader keeps the document open, the new file needs a
                // fresh session of the engine.
                PdfEngine.release(file)
                target.copyTo(file, overwrite = true)
            }
            Toast.makeText(
                requireContext(),
                R.string.preview_annotations_saved,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /** Shows the page with the given number, it starts at one. */
    private fun jumpToPage(page: Int) {
        val index = page - 1
        if (index < 0 || index >= pageCount) return
        binding.pager.setCurrentItem(index, true)
    }

    /** Highlights the tool that is active. */
    private fun updateAnnotationToolbar() {
        val toolbar = binding.annotationToolbar
        val chips = listOf(
            toolbar.toolHighlight to AnnotationType.HIGHLIGHT,
            toolbar.toolUnderline to AnnotationType.UNDERLINE,
            toolbar.toolStrikethrough to AnnotationType.STRIKETHROUGH,
            toolbar.toolDraw to AnnotationType.DRAW,
            toolbar.toolNote to AnnotationType.NOTE,
            toolbar.toolSignature to AnnotationType.SIGNATURE,
        )
        val colour = ContextCompat.getColor(requireContext(), R.color.brand_primary)
        val idle = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        chips.forEach { (chip, type) ->
            chip.setTextColor(if (activeTool == type) colour else idle)
            chip.isSelected = activeTool == type
        }
    }

    private fun selectTool(type: AnnotationType) {
        activeTool = type
        updateAnnotationToolbar()
        when (type) {
            AnnotationType.HIGHLIGHT,
            AnnotationType.UNDERLINE,
            AnnotationType.STRIKETHROUGH,
            -> {
                pagerAdapter?.applySelection(true) { _, rect -> onAreaSelected(type, rect) }
                Toast.makeText(
                    requireContext(),
                    R.string.preview_select_area,
                    Toast.LENGTH_SHORT,
                ).show()
            }

            AnnotationType.DRAW -> startDrawing()
            AnnotationType.NOTE -> showNoteDialog()
            AnnotationType.SIGNATURE -> launchSignatureEditor()
        }
    }

    private fun onAreaSelected(type: AnnotationType, rect: RectF) {
        addAnnotation(type, AnnotationPayload.encodeRect(rect))
        activeTool = null
        pagerAdapter?.applySelection(false, null)
        updateAnnotationToolbar()
    }

    private fun showNoteDialog() {
        val dialog = DialogNote.create()
        dialog.onNoteEntered = { text ->
            if (text.isNotEmpty()) addAnnotation(AnnotationType.NOTE, text)
        }
        dialog.show(parentFragmentManager, "preview_note")
    }

    private fun addAnnotation(type: AnnotationType, payload: String) {
        val doc = document ?: return
        repository.addAnnotation(
            Annotation(
                id = UUID.randomUUID().toString(),
                docId = doc.id,
                page = currentPage,
                type = type,
                color = annotationColour,
                text = payload,
            ),
        )
        pagerAdapter?.refreshMarks(currentPage)
    }

    // ------------------------------------------------------------------ editors

    private fun launchSignatureEditor() {
        signatureLauncher.launch(SignatureActivity.createIntent(requireContext()))
    }

    // ------------------------------------------------------------------ drawing

    /**
     * The tools of the drawing mode: the colours and the stroke sizes.
     *
     * The tool bar is the one of the annotation editor (design: draw), so the
     * ink looks the same in both places.
     */
    private fun setupDrawToolbar() {
        val palette = binding.drawToolbar.palette
        // The design shows seven colours; more of them are added so the strip
        // has to be scrolled on a small screen.
        palette.colors = DRAW_COLOURS
        palette.swatchDiameter = dp(24f)
        palette.itemSpacing = dp(20f)
        palette.ringColor = Color.WHITE
        palette.selectedIndex = 0
        palette.onColorSelected = { colour ->
            drawColour = colour
            pagerAdapter?.inkColour = colour
        }
        // The design shows the brush of the tool bar alone, without the tile.
        binding.drawToolbar.ivDraw.colorFilter = IconFilters.whiteGlyph()
        binding.drawToolbar.root.background = GradientDrawable().apply {
            // The panel of the design is rounded on top only.
            cornerRadii = floatArrayOf(dp(16f), dp(16f), dp(16f), dp(16f), 0f, 0f, 0f, 0f)
            setColor(DRAW_TOOLBAR_BACKGROUND)
        }
        buildSizePresets()
        drawWidth = dp(STROKE_SIZES[1])
    }

    /**
     * Builds the three stroke sizes of the design.
     *
     * Every preset is a white dot; the dot grows with the size of the stroke.
     */
    private fun buildSizePresets() {
        val sizes = binding.drawToolbar.llSizes
        sizes.removeAllViews()
        SIZE_DOTS.forEachIndexed { index, dot ->
            val frame = FrameLayout(requireContext())
            val params = LinearLayout.LayoutParams(dp(24), dp(24))
            if (index < SIZE_DOTS.lastIndex) params.marginEnd = dp(30)
            frame.layoutParams = params

            val dotView = View(requireContext())
            dotView.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            val diameter = dp(dot).toInt()
            frame.addView(dotView, FrameLayout.LayoutParams(diameter, diameter, Gravity.CENTER))

            frame.setOnClickListener {
                drawWidth = dp(STROKE_SIZES[index])
                pagerAdapter?.inkWidth = drawWidth
            }
            sizes.addView(frame)
        }
    }

    /**
     * The bars of the drawing mode take the insets of the system bars.
     *
     * The bar of the reader grows by the inset of the status bar and its
     * content moves down by half of it; the bar of the drawing mode does the
     * same, the tool bar below grows by the inset of the navigation bar.
     */
    private fun applyEditorInsets() {
        val top = binding.editorBar
        val bottom = binding.drawToolbar.root
        var topHeight = 0
        var bottomHeight = 0
        ViewCompat.setOnApplyWindowInsetsListener(top) { view, insets ->
            val inset = SystemBarInsets.top(insets)
            val params = view.layoutParams
            if (params.height > 0) {
                if (topHeight == 0) topHeight = params.height
                if (params.height != topHeight + inset) {
                    params.height = topHeight + inset
                    view.layoutParams = params
                }
            }
            // The content of the bar is centred, so it moves down by half of
            // the inset of the status bar.
            for (index in 0 until (view as ViewGroup).childCount) {
                view.getChildAt(index).translationY = inset / 2f
            }
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(bottom) { view, insets ->
            val inset = SystemBarInsets.bottom(insets)
            val params = view.layoutParams
            if (params.height > 0) {
                if (bottomHeight == 0) bottomHeight = params.height
                if (params.height != bottomHeight + inset) {
                    params.height = bottomHeight + inset
                    view.layoutParams = params
                }
            }
            insets
        }
    }

    /**
     * Shows the bar of the drawing mode (design: draw).
     *
     * The reader hands its bars over to the editor: the page keeps its whole
     * height and the tools of the annotation are away.
     */
    private fun showDrawBar() {
        binding.topBar.visibility = View.GONE
        binding.editorBar.visibility = View.VISIBLE
        binding.ivUndo.visibility = View.VISIBLE
        binding.ivRedo.visibility = View.VISIBLE
        binding.flDrawDone.visibility = View.VISIBLE
        binding.flSignDone.visibility = View.GONE
        binding.annotationToolbar.root.visibility = View.GONE
        binding.bottomBar.visibility = View.GONE
        binding.drawToolbar.root.visibility = View.VISIBLE
        binding.addBar.visibility = View.GONE
        // The design shows the pages without the number in the editors.
        pagerAdapter?.showPageBadge = false
    }

    /**
     * Shows the bar of the signature mode (design: sign-down).
     *
     * The bar carries the close button and - while the signature waits for its
     * place - the confirm pill that writes it into the document.
     */
    private fun showSignatureBar(confirm: Boolean) {
        binding.topBar.visibility = View.GONE
        binding.editorBar.visibility = View.VISIBLE
        binding.ivUndo.visibility = View.GONE
        binding.ivRedo.visibility = View.GONE
        binding.flDrawDone.visibility = View.GONE
        binding.flSignDone.visibility = if (confirm) View.VISIBLE else View.GONE
        binding.annotationToolbar.root.visibility = View.GONE
        binding.bottomBar.visibility = View.GONE
        binding.addBar.visibility = View.GONE
        // The design shows the pages without the number in the editors.
        pagerAdapter?.showPageBadge = false
    }

    /** Gives the reader its own bars back. */
    private fun showReaderBars() {
        binding.topBar.visibility = View.VISIBLE
        binding.editorBar.visibility = View.GONE
        binding.ivUndo.visibility = View.VISIBLE
        binding.ivRedo.visibility = View.VISIBLE
        binding.flDrawDone.visibility = View.VISIBLE
        binding.flSignDone.visibility = View.GONE
        binding.annotationToolbar.root.visibility =
            if (document?.type == DocType.PDF) View.VISIBLE else View.GONE
        binding.bottomBar.visibility = View.VISIBLE
        binding.addBar.visibility = View.GONE
        pagerAdapter?.showPageBadge = true
    }

    /**
     * Turns the drawing on.
     *
     * The pages take the ink of the finger while the reader stays readable:
     * a swipe that runs across the page turns it, so the drawing can walk
     * through the whole document.
     */
    private fun startDrawing() {
        drawMode = true
        activeTool = null
        updateAnnotationToolbar()
        // The selection of the other tools would eat the strokes.
        pagerAdapter?.applySelection(false, null)
        showDrawBar()
        ViewCompat.requestApplyInsets(binding.editorBar)
        ViewCompat.requestApplyInsets(binding.drawToolbar.root)
        val adapter = pagerAdapter ?: return
        adapter.inkEnabled = true
        adapter.inkProvider = { page -> inkPages[page].orEmpty() }
        adapter.onInkStroke = { page, stroke -> addInkStroke(page, stroke) }
        adapter.onPageSwipe = { direction -> turnPage(direction) }
        adapter.inkColour = drawColour
        adapter.inkWidth = drawWidth
        updateDrawButtons()
    }

    /** Turns the drawing off, the ink stays on the pages. */
    private fun stopDrawing() {
        if (!drawMode) return
        drawMode = false
        binding.drawToolbar.root.visibility = View.GONE
        showReaderBars()
        pagerAdapter?.inkEnabled = false
        updateDrawButtons()
    }

    /** Keeps the stroke of the finger on the page it was drawn on. */
    private fun addInkStroke(page: Int, stroke: InkStroke) {
        inkPages.getOrPut(page) { ArrayList() }.add(stroke)
        // A new stroke takes the undone ones away.
        inkRedoPages.remove(page)
        pagerAdapter?.refreshInk(page)
        updateDrawButtons()
    }

    /** Takes the last stroke of the page back. */
    private fun undoInk() {
        val page = currentPage
        val strokes = inkPages[page] ?: return
        val stroke = strokes.removeLastOrNull() ?: return
        inkRedoPages.getOrPut(page) { ArrayList() }.add(stroke)
        pagerAdapter?.refreshInk(page)
        updateDrawButtons()
    }

    /** Puts the last stroke of the page back. */
    private fun redoInk() {
        val page = currentPage
        val stack = inkRedoPages[page] ?: return
        val stroke = stack.removeLastOrNull() ?: return
        inkPages.getOrPut(page) { ArrayList() }.add(stroke)
        pagerAdapter?.refreshInk(page)
        updateDrawButtons()
    }

    /** The undo and the redo follow the ink of the page the reader is on. */
    private fun updateDrawButtons() {
        val canUndo = inkPages[currentPage].orEmpty().isNotEmpty()
        val canRedo = inkRedoPages[currentPage].orEmpty().isNotEmpty()
        binding.ivUndo.setImageResource(
            if (canUndo) R.mipmap.ic_undo_enable else R.mipmap.ic_undo_unable,
        )
        binding.ivRedo.setImageResource(
            if (canRedo) R.mipmap.ic_restore_enable else R.mipmap.ic_restore_unable,
        )
    }

    /** Turns the page of the reader. */
    private fun turnPage(direction: Int) {
        val target = currentPage + direction
        if (target < 0 || target >= pageCount) return
        binding.pager.setCurrentItem(target, true)
    }

    /**
     * Writes the ink of the drawing into the document.
     *
     * The ink becomes an annotation of the page, so it is painted again the
     * next time the document is opened.
     */
    private fun saveDrawing() {
        val doc = document ?: return
        inkPages.forEach { (page, strokes) ->
            if (strokes.isEmpty()) return@forEach
            repository.addAnnotation(
                Annotation(
                    id = UUID.randomUUID().toString(),
                    docId = doc.id,
                    page = page,
                    type = AnnotationType.DRAW,
                    color = annotationColour,
                    text = AnnotationPayload.encodeInk(strokes),
                ),
            )
        }
        inkPages.clear()
        inkRedoPages.clear()
        stopDrawing()
        pagerAdapter?.refreshMarks()
        Toast.makeText(requireContext(), R.string.preview_draw_done, Toast.LENGTH_SHORT).show()
    }

    /**
     * Puts the signature of the editor on the page.
     *
     * The signature waits inside the selection box of the design: it can be
     * dragged to any place of the page and scaled with the handle in its
     * bottom right corner. The confirm pill of the reader writes it into the
     * document, the red button throws it away.
     */
    private fun handleSignatureResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) return
        val intent = data ?: return
        val payload = intent.getStringExtra(SignatureActivity.EXTRA_INK) ?: return
        val aspect = intent.getFloatExtra(SignatureActivity.EXTRA_ASPECT, 1f)
        val strokes = AnnotationPayload.decodeInk(payload)
        if (strokes.isEmpty()) return
        val view = currentPageView()
        if (view == null) {
            Toast.makeText(requireContext(), R.string.preview_signature_failed, Toast.LENGTH_SHORT).show()
            return
        }
        view.onPlacementCancelled = {
            // The signature is thrown away and the reader closes itself:
            // nothing is written into the document.
            requireActivity().finish()
        }
        view.beginSignaturePlacement(strokes, aspect)
        showSignatureBar(confirm = true)
    }

    /**
     * Shows or hides the confirmation bar of the design.
     *
     * The bar appears once the signature rests on the page; it writes the
     * signature into the document.
     */
    private fun showAddBar(visible: Boolean) {
        binding.addBar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * Writes the placed signature into the document.
     *
     * The signature is drawn into the page of the file, so the document
     * carries it from now on. The strokes are drawn as vectors, the text of
     * the page stays readable and the signature stays sharp at every zoom.
     */
    private fun saveSignature() {
        val doc = document ?: return
        val view = currentPageView() ?: return
        val placed = view.commitSignaturePlacement() ?: return
        val file = repository.fileOf(doc)
        val page = currentPage
        binding.flSignDone.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val target = File(
                File(requireContext().cacheDir, "signed").apply { mkdirs() },
                "${doc.id}.pdf",
            )
            val strokes = placed.map { it.points }
            val colour = placed.firstOrNull()?.color ?: Color.BLACK
            val width = placed.firstOrNull()?.width ?: DEFAULT_SIGNATURE_WIDTH
            val written = withContext(Dispatchers.IO) {
                PdfEngine.writeStrokes(file, target, page + 1, strokes, colour, width)
            }
            if (!written) {
                binding.flSignDone.isEnabled = true
                Toast.makeText(
                    requireContext(),
                    R.string.preview_signature_failed,
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            withContext(Dispatchers.IO) {
                // The reader keeps the document open, the new file needs a
                // fresh session of the engine.
                PdfEngine.release(file)
                target.copyTo(file, overwrite = true)
            }
            // The page shows the signature that was written into the file.
            currentPageView()?.refresh()
            showSignatureBar(confirm = false)
            showAddBar(true)
            activeTool = null
            Toast.makeText(requireContext(), R.string.preview_signature_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun currentPageView(): PdfPageView? = pagerAdapter?.pageView(currentPage)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ARG_DOC_ID = "com.peng.pdfreader.extra.DOC_ID"

        /** The name of the file is smaller than a page title. */
        private const val TITLE_SIZE_SP = 15f

        /**
         * The width of a signature, as a fraction of the width of the page.
         *
         * It is only used when the placed signature does not carry a width of
         * its own.
         */
        private const val DEFAULT_SIGNATURE_WIDTH = 0.01f

        /** The confirmation bar of the design: 80 % black. */
        private const val ADD_BAR_BACKGROUND = 0xCC313131.toInt()

        /** The tool bar of the drawing mode: 80 % black. */
        private const val DRAW_TOOLBAR_BACKGROUND = 0xCC313131.toInt()

        /** The dots of the stroke sizes of the design. */
        private val SIZE_DOTS = listOf(6f, 12f, 24f)

        /** The stroke widths the dots stand for, in pixels. */
        private val STROKE_SIZES = listOf(2f, 4f, 8f)

        /** The colours of the ink in the drawing mode. */
        private val DRAW_COLOURS: List<Int> = ColorPaletteView.DEFAULT_COLORS + listOf(
            Color.parseColor("#00bcd4"),
            Color.parseColor("#d9d9d9"),
            Color.parseColor("#795548"),
        )


        /** Creates the reader for the given document. */
        fun newInstance(docId: String): PdfPreviewFragment = PdfPreviewFragment().apply {
            arguments = Bundle().apply { putString(ARG_DOC_ID, docId) }
        }
    }
}
