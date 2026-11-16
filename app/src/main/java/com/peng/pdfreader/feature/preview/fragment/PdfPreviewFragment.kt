package com.peng.pdfreader.feature.preview.fragment

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.pdf.PdfEngine
import com.peng.pdfreader.appcore.pdf.PdfVault
import com.peng.pdfreader.appcore.widget.PdfPageView
import com.peng.pdfreader.appcore.widget.ReadingMode
import com.peng.pdfreader.base.BaseBackHandler
import com.peng.pdfreader.base.BaseFragment
import com.peng.pdfreader.data.model.DocType
import com.peng.pdfreader.data.model.Document
import com.peng.pdfreader.data.prefs.AppPrefs
import com.peng.pdfreader.data.repository.Annotation
import com.peng.pdfreader.data.repository.AnnotationType
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.databinding.FragmentPdfPreviewBinding
import com.peng.pdfreader.feature.lock.sheet.PasswordSheet
import com.peng.pdfreader.feature.preview.activity.DrawActivity
import com.peng.pdfreader.feature.preview.activity.SignatureActivity
import com.peng.pdfreader.feature.preview.adapter.PdfPagerAdapter
import com.peng.pdfreader.feature.preview.annotation.AnnotationMarks
import com.peng.pdfreader.feature.preview.annotation.AnnotationPayload
import com.peng.pdfreader.feature.preview.dialog.MoreActionsSheet
import com.peng.pdfreader.feature.preview.dialog.FailureDialog
import com.peng.pdfreader.feature.preview.dialog.NoteDialog
import com.peng.pdfreader.feature.preview.dialog.OutlineSheet
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
import java.util.UUID

/**
 * The PDF reader.
 *
 * The pages are rendered by `:appcore` on a background thread, the reading
 * position is persisted and the annotations are written through the
 * repository.
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
    private var activeTool: AnnotationType? = null
    private var passwordSheet: PasswordSheet? = null
    private val annotationColour = Color.parseColor("#fb4141")

    private val signatureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> handleSignatureResult(result.resultCode, result.data) }

    private val drawLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result -> handleDrawResult(result.resultCode, result.data) }

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

        binding.titleBar.title = doc.name
        // The viewer shows the name of the file with 15sp / medium.
        binding.titleBar.setTitleTypography(TITLE_SIZE_SP, true)
        binding.titleBar.showBack = true
        binding.titleBar.onBackClick = {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.titleBar.addAction(buildTitleActions())

        binding.ivFavorite.setImageResource(
            if (doc.isFavorite) R.mipmap.ic_star_select else R.mipmap.ic_star_unselect,
        )
        binding.ivFavorite.setOnClickListener { toggleFavorite() }
        binding.tvFavorite.setOnClickListener { toggleFavorite() }
        binding.ivAnnotate.setOnClickListener { toggleAnnotationBar() }
        binding.ivOpenWith.setOnClickListener { openWith() }
        binding.tvOpenWith.setOnClickListener { openWith() }
        binding.ivShare.setOnClickListener { share() }
        binding.tvShare.setOnClickListener { share() }

        // The confirmation bar of the design carries the signature over.
        binding.addBar.background = GradientDrawable().apply {
            setColor(ADD_BAR_BACKGROUND)
        }
        binding.ivAdd.setImageResource(R.mipmap.ic_sign)
        binding.addBar.setOnClickListener { commitPlacedSignature() }

        binding.annotationBar.root.visibility = View.GONE
        binding.annotationBar.toolHighlight.setOnClickListener { selectTool(AnnotationType.HIGHLIGHT) }
        binding.annotationBar.toolUnderline.setOnClickListener { selectTool(AnnotationType.UNDERLINE) }
        binding.annotationBar.toolStrikethrough.setOnClickListener {
            selectTool(AnnotationType.STRIKETHROUGH)
        }
        binding.annotationBar.toolDraw.setOnClickListener { selectTool(AnnotationType.DRAW) }
        binding.annotationBar.toolNote.setOnClickListener { selectTool(AnnotationType.NOTE) }
        binding.annotationBar.toolSignature.setOnClickListener { selectTool(AnnotationType.SIGNATURE) }

        binding.pager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    currentPage = position
                    updateIndicator()
                }
            },
        )

        loadDocument()
        updateToolSelection()
    }

    override fun onPause() {
        super.onPause()
        persistProgress()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pagerAdapter = null
        _binding = null
    }

    override fun handleBackPressed(): Boolean {
        val page = currentPageView()
        if (page != null && page.hasPendingPlacement) {
            page.cancelSignaturePlacement()
            return true
        }
        val bar = _binding?.annotationBar?.root ?: return false
        if (bar.visibility == View.VISIBLE) {
            closeAnnotationBar()
            return true
        }
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

    private fun openDocument(doc: Document, file: File) {
        viewLifecycleOwner.lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                if (doc.type == DocType.IMAGE) 1 else PdfEngine.pageCount(file)
            }
            if (_binding == null) return@launch
            if (count <= 0 && doc.type != DocType.IMAGE) {
                // The file is broken or it is not a PDF at all - the failure
                // dialog of the design explains it.
                showOpenFailure(doc)
                return@launch
            }
            pageCount = count.coerceAtLeast(1)
            val adapter = PdfPagerAdapter(
                file,
                pageCount,
                readingMode(),
                doc.type == DocType.IMAGE,
            )
            adapter.marksProvider = { page ->
                AnnotationMarks.marksOf(repository.annotationsOf(doc.id), page)
            }
            pagerAdapter = adapter
            binding.pager.adapter = adapter
            val start = (doc.lastPage - 1).coerceIn(0, pageCount - 1)
            if (start > 0) binding.pager.setCurrentItem(start, false)
            currentPage = start
            updateIndicator()
        }
    }

    // ------------------------------------------------------------------ locked documents

    /**
     * The document cannot be opened.
     *
     * The dialog of the design explains it, the page stays open so the user
     * can go back to the list.
     */
    private fun showOpenFailure(doc: Document) {
        val message = if (doc.type == DocType.PDF) {
            getString(R.string.preview_open_invalid)
        } else {
            getString(R.string.preview_open_failed)
        }
        FailureDialog.show(childFragmentManager, message)
    }

    /**
     * A locked document is a vault container, the renderer cannot open it.
     * The password sheet asks for the password, the decrypted copy is then
     * rendered from the cache.
     */
    private fun requestPassword(doc: Document, file: File, errorMessage: String? = null) {
        passwordSheet?.dismiss()
        val sheet = PasswordSheet.newInstance(PasswordSheet.Mode.ENTER)
        passwordSheet = sheet
        errorMessage?.let { message ->
            sheet.showErrorOnShow = message
        }
        childFragmentManager.setFragmentResultListener(
            PasswordSheet.RESULT_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            val password = bundle.getString(PasswordSheet.KEY_PASSWORD).orEmpty()
            unlockAndOpen(doc, file, password)
        }
        sheet.show(childFragmentManager, PasswordSheet.TAG)
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

    private fun updateIndicator() {
        if (_binding == null) return
        binding.tvPageIndicator.text = getString(
            R.string.preview_page_indicator,
            currentPage + 1,
            pageCount,
        )
    }

    private fun persistProgress() {
        val doc = document ?: return
        if (pageCount <= 0) return
        repository.setProgress(doc, currentPage + 1)
    }

    private fun readingMode(): ReadingMode = prefs.readingMode

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
        val file = repository.fileOf(doc)
        FileSharing.share(requireContext(), file, FileSharing.mimeTypeOf(doc.name))
    }

    private fun openWith() {
        val doc = document ?: return
        val file = repository.fileOf(doc)
        val opened = FileSharing.openWith(requireContext(), file, FileSharing.mimeTypeOf(doc.name))
        if (!opened) {
            Toast.makeText(requireContext(), R.string.no_permissions, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * The three actions of the app bar: rename, share and the overflow.
     *
     * The design puts the rename tile at x=174, the share at x=276 and the
     * overflow at x=316.
     */
    private fun buildTitleActions(): View = PreviewTitleActions.build(
        requireContext(),
        onRename = { showRenameDialog() },
        onShare = { share() },
        onMore = { showMoreActions() },
    )

    private fun showMoreActions() {
        val doc = document
        val sheet = MoreActionsSheet.create(
            actions = listOf(
                MoreActionsSheet.Action(
                    ACTION_OUTLINE,
                    getString(R.string.preview_outline),
                    R.mipmap.ic_menu,
                ),
                MoreActionsSheet.Action(
                    ACTION_BOOKMARK,
                    getString(R.string.preview_add_bookmark),
                    R.mipmap.ic_collect_unselect,
                ),
                MoreActionsSheet.Action(
                    ACTION_READING_MODE,
                    getString(R.string.preview_reading_mode),
                    R.mipmap.ic_eye_open,
                ),
                MoreActionsSheet.Action(
                    ACTION_RENAME,
                    getString(R.string.rename),
                    R.mipmap.ic_rename_black,
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
                ACTION_OUTLINE -> showOutline()
                ACTION_BOOKMARK -> addBookmark()
                ACTION_READING_MODE -> showReadingModeDialog()
                ACTION_RENAME -> showRenameDialog()
                ACTION_SHARE -> share()
                ACTION_COMPRESS -> compress()
            }
        }
        sheet.show(parentFragmentManager, "preview_more")
    }

    /** Opens the compressor of the app for the current document. */
    private fun compress() {
        val doc = document ?: return
        CompressActivity.start(requireContext(), doc.id)
    }

    /** The meta line of the more sheet: the date and the size of the file. */
    private fun documentMeta(): String {
        val doc = document ?: return ""
        val date = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(doc.modifiedAt)
        val size = Formatter.formatShortFileSize(requireContext(), doc.sizeBytes)
        return "$date · $size"
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

    private fun addBookmark() {
        val doc = document ?: return
        repository.addBookmark(doc.id, currentPage + 1, getString(R.string.preview_cd_page, currentPage + 1))
        Toast.makeText(requireContext(), R.string.preview_bookmark_added, Toast.LENGTH_SHORT).show()
    }

    private fun showOutline() {
        val doc = document ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                PdfEngine.extractOutline(repository.fileOf(doc))
            }
            if (_binding == null) return@launch
            if (entries.isEmpty()) {
                Toast.makeText(requireContext(), R.string.no_outline, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val sheet = OutlineSheet.create(entries.map { it.title }, entries.map { it.page })
            sheet.onEntrySelected = { index ->
                val page = entries.getOrNull(index)?.page
                if (page != null) jumpToPage(page - 1)
            }
            sheet.show(parentFragmentManager, "preview_outline")
        }
    }

    private fun jumpToPage(index: Int) {
        if (pageCount <= 0) return
        binding.pager.setCurrentItem(index.coerceIn(0, pageCount - 1), true)
    }

    private fun showReadingModeDialog() {
        val labels = arrayOf(
            getString(R.string.preview_mode_light),
            getString(R.string.preview_mode_sepia),
            getString(R.string.preview_mode_night),
            getString(R.string.preview_mode_black),
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.preview_reading_mode)
            .setItems(labels) { _, which ->
                val mode = when (which) {
                    0 -> ReadingMode.LIGHT
                    1 -> ReadingMode.SEPIA
                    2 -> ReadingMode.NIGHT_INVERT
                    else -> ReadingMode.BLACK
                }
                prefs.readingMode = mode
                pagerAdapter?.applyReadingMode(readingMode())
            }
            .show()
    }

    // ------------------------------------------------------------------ annotations

    private fun toggleAnnotationBar() {
        val bar = binding.annotationBar.root
        if (bar.visibility == View.VISIBLE) {
            closeAnnotationBar()
        } else {
            bar.visibility = View.VISIBLE
        }
    }

    private fun closeAnnotationBar() {
        binding.annotationBar.root.visibility = View.GONE
        activeTool = null
        pagerAdapter?.applySelection(false, null)
        updateToolSelection()
    }

    private fun selectTool(type: AnnotationType) {
        activeTool = type
        updateToolSelection()
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

            AnnotationType.DRAW -> launchDrawEditor()
            AnnotationType.NOTE -> showNoteDialog()
            AnnotationType.SIGNATURE -> launchSignatureEditor()
        }
    }

    private fun updateToolSelection() {
        val tools = listOf(
            binding.annotationBar.toolHighlight to AnnotationType.HIGHLIGHT,
            binding.annotationBar.toolUnderline to AnnotationType.UNDERLINE,
            binding.annotationBar.toolStrikethrough to AnnotationType.STRIKETHROUGH,
            binding.annotationBar.toolDraw to AnnotationType.DRAW,
            binding.annotationBar.toolNote to AnnotationType.NOTE,
            binding.annotationBar.toolSignature to AnnotationType.SIGNATURE,
        )
        tools.forEach { (view, type) ->
            val selected = type == activeTool
            view.setBackgroundResource(
                if (selected) R.drawable.bg_icon_tile else R.drawable.bg_type_chip,
            )
            view.setTextColor(
                requireContext().getColor(
                    if (selected) R.color.brand_primary else R.color.text_secondary,
                ),
            )
        }
    }

    private fun onAreaSelected(type: AnnotationType, rect: RectF) {
        addAnnotation(type, AnnotationPayload.encodeRect(rect))
        activeTool = null
        pagerAdapter?.applySelection(false, null)
        updateToolSelection()
    }

    private fun showNoteDialog() {
        val dialog = NoteDialog.create()
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
                page = currentPage + 1,
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

    private fun launchDrawEditor() {
        val doc = document ?: return
        val file = repository.fileOf(doc)
        drawLauncher.launch(
            DrawActivity.createIntent(
                requireContext(),
                file,
                currentPage,
                doc.type == DocType.IMAGE,
            ),
        )
    }

    /**
     * Puts the signature of the editor on the page.
     *
     * The signature waits inside the selection box of the design until the
     * user drags it into place and confirms it with the green button; the
     * "Add" bar of the design then writes it into the document.
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
        view.onPlacementAccepted = { showAddBar(true) }
        view.onPlacementCancelled = {
            showAddBar(false)
            activeTool = null
            updateToolSelection()
        }
        view.beginSignaturePlacement(strokes, aspect)
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

    /** Writes the placed signature into the document. */
    private fun commitPlacedSignature() {
        val placed = currentPageView()?.commitSignaturePlacement()
        if (placed == null) {
            showAddBar(false)
            return
        }
        addAnnotation(AnnotationType.SIGNATURE, AnnotationPayload.encodeInk(placed))
        showAddBar(false)
        activeTool = null
        updateToolSelection()
        Toast.makeText(requireContext(), R.string.preview_signature_saved, Toast.LENGTH_SHORT).show()
    }

    private fun handleDrawResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) return
        val intent = data ?: return
        val payload = intent.getStringExtra(DrawActivity.EXTRA_INK) ?: return
        addAnnotation(AnnotationType.DRAW, payload)
        val output = intent.getStringExtra(DrawActivity.EXTRA_OUTPUT)
        if (!output.isNullOrEmpty()) registerAnnotatedDocument(output)
        Toast.makeText(requireContext(), R.string.preview_draw_saved, Toast.LENGTH_SHORT).show()
    }

    private fun registerAnnotatedDocument(path: String) {
        val doc = document ?: return
        val source = File(path)
        if (!source.exists()) return
        val base = doc.name.substringBeforeLast('.', doc.name)
        val extension = doc.name.substringAfterLast('.', "pdf")
        val name = "${base}_${getString(R.string.annotated_suffix)}.$extension"
        repository.addExisting(name, doc.type, source)
    }

    private fun currentPageView(): PdfPageView? = pagerAdapter?.pageView(currentPage)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ARG_DOC_ID = "com.peng.pdfreader.extra.DOC_ID"

        private const val ACTION_OUTLINE = "outline"
        private const val ACTION_BOOKMARK = "bookmark"
        private const val ACTION_READING_MODE = "reading_mode"
        private const val ACTION_RENAME = "rename"
        private const val ACTION_SHARE = "share"
        private const val ACTION_COMPRESS = "compress"

        /** The name of the file is smaller than a page title. */
        private const val TITLE_SIZE_SP = 15f

        /** The confirmation bar of the design: 80 % black. */
        private const val ADD_BAR_BACKGROUND = 0xCC313131.toInt()


        /** Creates the reader for the given document. */
        fun newInstance(docId: String): PdfPreviewFragment = PdfPreviewFragment().apply {
            arguments = Bundle().apply { putString(ARG_DOC_ID, docId) }
        }
    }
}
