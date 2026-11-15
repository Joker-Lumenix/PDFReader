package com.peng.pdfreader.feature.preview.fragment

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
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.pdf.PdfEngine
import com.peng.pdfreader.appcore.pdf.PdfVault
import com.peng.pdfreader.appcore.widget.InkStroke
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
import com.peng.pdfreader.feature.preview.dialog.NoteDialog
import com.peng.pdfreader.feature.preview.dialog.OutlineSheet
import com.peng.pdfreader.feature.preview.dialog.RenameDialog
import com.peng.pdfreader.feature.preview.util.FileSharing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
        binding.titleBar.showBack = true
        binding.titleBar.onBackClick = {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.titleBar.addAction(buildRenameAction())
        binding.titleBar.addAction(buildMoreAction())

        binding.ivFavorite.setImageResource(
            if (doc.isFavorite) R.mipmap.ic_star_select else R.mipmap.ic_star_unselect,
        )
        binding.actionFavorite.setOnClickListener { toggleFavorite() }
        binding.actionAnnotate.setOnClickListener { toggleAnnotationBar() }
        binding.actionOpenWith.setOnClickListener { openWith() }
        binding.actionShare.setOnClickListener { share() }

        binding.annotationBar.llTools.visibility = View.GONE
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
        val tools = _binding?.annotationBar?.llTools ?: return false
        if (tools.visibility == View.VISIBLE) {
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
     * A locked document is a vault container, the renderer cannot open it.
     * The password sheet asks for the password, the decrypted copy is then
     * rendered from the cache.
     */
    private fun requestPassword(doc: Document, file: File, errorMessage: String? = null) {
        passwordSheet?.dismiss()
        val sheet = PasswordSheet.newInstance(PasswordSheet.Mode.UNLOCK)
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

    private fun buildRenameAction(): View {
        val tile = FrameLayout(requireContext())
        tile.background = GradientDrawable().apply {
            setColor(requireContext().getColor(R.color.brand_primary_container))
            cornerRadius = dp(6.4f)
        }
        val icon = ImageView(requireContext()).apply {
            setImageResource(R.mipmap.ic_edit_black)
            contentDescription = getString(R.string.preview_cd_rename)
        }
        tile.addView(icon, FrameLayout.LayoutParams(dp(12), dp(14), Gravity.CENTER))
        tile.contentDescription = getString(R.string.preview_cd_rename)
        tile.setOnClickListener { showRenameDialog() }
        return tile
    }

    private fun buildMoreAction(): View = ImageView(requireContext()).apply {
        setImageResource(R.mipmap.ic_more)
        contentDescription = getString(R.string.preview_cd_more)
        setOnClickListener { showMoreActions() }
        layoutParams = FrameLayout.LayoutParams(dp(24f).toInt(), dp(24f).toInt())
    }

    private fun showMoreActions() {
        val sheet = MoreActionsSheet.create(
            listOf(
                MoreActionsSheet.Action(ACTION_OUTLINE, getString(R.string.preview_outline)),
                MoreActionsSheet.Action(ACTION_BOOKMARK, getString(R.string.preview_add_bookmark)),
                MoreActionsSheet.Action(ACTION_READING_MODE, getString(R.string.preview_reading_mode)),
                MoreActionsSheet.Action(ACTION_RENAME, getString(R.string.rename)),
            ),
        )
        sheet.onActionSelected = { id ->
            when (id) {
                ACTION_OUTLINE -> showOutline()
                ACTION_BOOKMARK -> addBookmark()
                ACTION_READING_MODE -> showReadingModeDialog()
                ACTION_RENAME -> showRenameDialog()
            }
        }
        sheet.show(parentFragmentManager, "preview_more")
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
        val tools = binding.annotationBar.llTools
        if (tools.visibility == View.VISIBLE) {
            closeAnnotationBar()
        } else {
            tools.visibility = View.VISIBLE
        }
    }

    private fun closeAnnotationBar() {
        binding.annotationBar.llTools.visibility = View.GONE
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

    private fun handleSignatureResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) return
        val payload = data?.getStringExtra(SignatureActivity.EXTRA_INK) ?: return
        val canvasSize = data.getFloatArrayExtra(SignatureActivity.EXTRA_CANVAS) ?: return
        val bounds = data.getFloatArrayExtra(SignatureActivity.EXTRA_BOUNDS) ?: return
        val view = currentPageView()
        val aspect = if (view != null && view.pageHeightPt > 0) {
            view.pageWidthPt.toFloat() / view.pageHeightPt
        } else {
            595f / 842f
        }
        val placed = placeInk(AnnotationPayload.decodeInk(payload), canvasSize, bounds, aspect)
        if (placed.isEmpty()) return
        addAnnotation(AnnotationType.SIGNATURE, AnnotationPayload.encodeInk(placed))
        Toast.makeText(requireContext(), R.string.preview_signature_saved, Toast.LENGTH_SHORT).show()
    }

    private fun handleDrawResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) return
        val payload = data?.getStringExtra(DrawActivity.EXTRA_INK) ?: return
        addAnnotation(AnnotationType.DRAW, payload)
        val output = data.getStringExtra(DrawActivity.EXTRA_OUTPUT)
        if (!output.isNullOrEmpty()) registerAnnotatedDocument(output)
        Toast.makeText(requireContext(), R.string.preview_draw_saved, Toast.LENGTH_SHORT).show()
    }

    /**
     * Moves the signature of the editor onto the page.
     *
     * The signature is scaled to a quarter of the page width and placed at the
     * lower left of the page, the way the design shows it.
     */
    private fun placeInk(
        strokes: List<InkStroke>,
        canvasSize: FloatArray,
        bounds: FloatArray,
        aspect: Float,
    ): List<InkStroke> {
        if (strokes.isEmpty()) return emptyList()
        val canvasWidth = canvasSize.getOrElse(0) { 1f }.coerceAtLeast(1f)
        val canvasHeight = canvasSize.getOrElse(1) { 1f }.coerceAtLeast(1f)
        val left = bounds.getOrElse(0) { 0f } * canvasWidth
        val top = bounds.getOrElse(1) { 0f } * canvasHeight
        val right = bounds.getOrElse(2) { 1f } * canvasWidth
        val bottom = bounds.getOrElse(3) { 1f } * canvasHeight
        val inkWidth = (right - left).coerceAtLeast(1f)
        val inkHeight = (bottom - top).coerceAtLeast(1f)
        val targetWidth = 0.32f
        val targetHeight = targetWidth * aspect * (inkHeight / inkWidth)
        val targetLeft = 0.08f
        val targetTop = 0.66f
        return strokes.map { stroke ->
            val points = FloatArray(stroke.points.size)
            var index = 0
            while (index + 1 < stroke.points.size) {
                val x = stroke.points[index] * canvasWidth
                val y = stroke.points[index + 1] * canvasHeight
                points[index] = targetLeft + ((x - left) / inkWidth) * targetWidth
                points[index + 1] = targetTop + ((y - top) / inkHeight) * targetHeight
                index += 2
            }
            InkStroke(stroke.color, stroke.width * targetWidth * canvasWidth / inkWidth, points)
        }
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

        /** Creates the reader for the given document. */
        fun newInstance(docId: String): PdfPreviewFragment = PdfPreviewFragment().apply {
            arguments = Bundle().apply { putString(ARG_DOC_ID, docId) }
        }
    }
}
