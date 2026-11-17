package com.peng.pdfreader.ui.activities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.peng.pdfreader.R
import com.peng.pdfreader.core.pdf.PdfEngine
import com.peng.pdfreader.core.widget.ColorPaletteView
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.databinding.ActivityDrawBinding
import com.peng.pdfreader.ui.uiData.AnnotationPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The free hand annotation editor.
 *
 * The page is rendered below the ink, the tool bar offers the stroke sizes
 * and the colours. Confirming the drawing bakes the ink into a copy of the
 * document and hands the strokes back to the caller.
 */
class DrawActivity : BaseActivity() {

    private lateinit var binding: ActivityDrawBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDrawBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivBack.setOnClickListener { finish() }
        binding.flDone.setOnClickListener { confirm() }
        binding.ivUndo.setOnClickListener {
            binding.canvas.undo()
            updateHistoryButtons()
        }
        binding.ivRedo.setOnClickListener {
            binding.canvas.redo()
            updateHistoryButtons()
        }

        binding.toolBar.background = GradientDrawable().apply {
            cornerRadii = floatArrayOf(dp(16f), dp(16f), dp(16f), dp(16f), 0f, 0f, 0f, 0f)
            setColor(TOOLBAR_BACKGROUND)
        }

        binding.palette.colors = ColorPaletteView.DEFAULT_COLORS.take(PALETTE_COLOURS)
        binding.palette.swatchDiameter = dp(24f)
        binding.palette.itemSpacing = dp(20f)
        binding.palette.ringColor = Color.WHITE
        binding.palette.selectedIndex = 0
        binding.palette.onColorSelected = { colour -> binding.canvas.strokeColor = colour }

        binding.canvas.strokeColor = binding.palette.selectedColor
        binding.canvas.strokeWidth = dp(STROKE_SIZES[0])
        binding.canvas.onStrokesChanged = { updateHistoryButtons() }

        buildSizePresets()
        updateHistoryButtons()
        renderPage()
    }

    /**
     * Builds the three stroke sizes of the design.
     *
     * Every preset is a white ring with a white dot in the middle; the dot
     * grows with the size of the stroke.
     */
    private fun buildSizePresets() {
        binding.llSizes.removeAllViews()
        SIZE_DOTS.forEachIndexed { index, dot ->
            val frame = FrameLayout(this)
            val params = LinearLayout.LayoutParams(dp(24), dp(24))
            if (index < SIZE_DOTS.lastIndex) params.marginEnd = dp(30)
            frame.layoutParams = params

            val ring = View(this)
            ring.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), Color.WHITE)
            }
            frame.addView(ring, FrameLayout.LayoutParams(dp(24), dp(24)))

            val dotView = View(this)
            dotView.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            val diameter = dp(dot).toInt()
            frame.addView(dotView, FrameLayout.LayoutParams(diameter, diameter, Gravity.CENTER))

            frame.setOnClickListener { binding.canvas.strokeWidth = dp(STROKE_SIZES[index]) }
            binding.llSizes.addView(frame)
        }
    }

    private fun updateHistoryButtons() {
        binding.ivUndo.setImageResource(
            if (binding.canvas.canUndo) R.mipmap.ic_undo_enable else R.mipmap.ic_undo_unable,
        )
        binding.ivRedo.setImageResource(
            if (binding.canvas.canRedo) R.mipmap.ic_restore_enable else R.mipmap.ic_restore_unable,
        )
    }

    private fun renderPage() {
        val file = sourceFile() ?: return
        val page = intent.getIntExtra(EXTRA_PAGE, 0)
        val image = intent.getBooleanExtra(EXTRA_IMAGE, false)
        lifecycleScope.launch {
            val rendered = withContext(Dispatchers.IO) {
                if (image) PdfEngine.renderImage(file) else PdfEngine.renderPage(file, page)
            }
            binding.canvas.pageBitmap = rendered?.bitmap
            binding.canvas.pageWidthPt = (rendered?.pageWidthPt ?: 595).toFloat()
            binding.canvas.pageHeightPt = (rendered?.pageHeightPt ?: 842).toFloat()
        }
    }

    private fun confirm() {
        val file = sourceFile() ?: return
        val page = intent.getIntExtra(EXTRA_PAGE, 0)
        val overlay = binding.canvas.exportOverlay()
        val ink = AnnotationPayload.encodeInk(binding.canvas.pageInkStrokes())
        val output = File(
            File(cacheDir, "annotated").apply { mkdirs() },
            annotatedName(file.name),
        )
        binding.flDone.isEnabled = false
        binding.flDone.alpha = 0.5f
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (overlay == null) {
                    null
                } else {
                    PdfEngine.flattenOverlay(file, output, page, overlay.bitmap, overlay.bounds)
                }
            }
            val data = Intent()
                .putExtra(EXTRA_INK, ink)
                .putExtra(EXTRA_OUTPUT, if (result?.isSuccess == true) output.absolutePath else "")
            setResult(RESULT_OK, data)
            finish()
        }
    }

    private fun sourceFile(): File? {
        val path = intent.getStringExtra(EXTRA_FILE) ?: return null
        val file = File(path)
        return if (file.exists()) file else null
    }

    private fun annotatedName(name: String): String {
        val base = name.substringBeforeLast('.', name)
        return "${base}_${getString(R.string.annotated_suffix)}.pdf"
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        /** The file that is annotated. */
        const val EXTRA_FILE = "com.peng.pdfreader.extra.DRAW_FILE"

        /** The page that is annotated. */
        const val EXTRA_PAGE = "com.peng.pdfreader.extra.DRAW_PAGE"

        /** Whether the file is a raster image instead of a PDF. */
        const val EXTRA_IMAGE = "com.peng.pdfreader.extra.DRAW_IMAGE"

        /** The strokes of the drawing. */
        const val EXTRA_INK = "com.peng.pdfreader.extra.DRAW_INK"

        /** The copy of the document that carries the annotation. */
        const val EXTRA_OUTPUT = "com.peng.pdfreader.extra.DRAW_OUTPUT"

        private val SIZE_DOTS = listOf(6f, 12f, 18f)
        private val STROKE_SIZES = listOf(2f, 4f, 8f)
        private const val PALETTE_COLOURS = 7
        private const val TOOLBAR_BACKGROUND = 0xCC313131.toInt()

        /** The intent that opens the editor. */
        fun createIntent(
            context: Context,
            file: File,
            page: Int,
            image: Boolean,
        ): Intent = Intent(context, DrawActivity::class.java)
            .putExtra(EXTRA_FILE, file.absolutePath)
            .putExtra(EXTRA_PAGE, page)
            .putExtra(EXTRA_IMAGE, image)
    }
}
