package com.peng.pdfreader.ui.activities

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import com.peng.pdfreader.R
import com.peng.pdfreader.core.widget.ColorPaletteView
import com.peng.pdfreader.core.widget.InkStroke
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.databinding.ActivitySignatureBinding
import com.peng.pdfreader.ui.uiData.AnnotationPayload

/**
 * The signature editor.
 *
 * The user signs on the canvas, picks a colour and confirms with the pill of
 * the design; the strokes are handed back to the caller.
 */
class SignatureActivity : BaseActivity() {

    private lateinit var binding: ActivitySignatureBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignatureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivClose.setOnClickListener { finish() }
        binding.flDone.setOnClickListener { confirm() }
        binding.ivUndo.setOnClickListener {
            binding.canvas.undo()
            updateHistoryButtons()
        }
        binding.ivRedo.setOnClickListener {
            binding.canvas.redo()
            updateHistoryButtons()
        }

        binding.canvas.placeholder = getString(R.string.sign_here)
        binding.canvas.strokeColor = DEFAULT_COLOUR
        binding.canvas.strokeWidth = dp(3f)
        binding.canvas.onStrokesChanged = { updateHistoryButtons() }

        binding.palette.colors = ColorPaletteView.DEFAULT_COLORS
        binding.palette.swatchDiameter = dp(20f)
        binding.palette.itemSpacing = dp(16f)
        binding.palette.ringColor = Color.WHITE
        binding.palette.selectedColor = DEFAULT_COLOUR
        binding.palette.onColorSelected = { colour ->
            binding.canvas.strokeColor = colour
            showSwatch(colour)
        }
        showSwatch(DEFAULT_COLOUR)

        val toggle = View.OnClickListener { togglePalette() }
        binding.tvColorLabel.setOnClickListener(toggle)
        binding.swatch.setOnClickListener(toggle)

        updateHistoryButtons()
    }

    private fun togglePalette() {
        val palette = binding.llPalette
        palette.visibility = if (palette.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    /** The back button closes the colour palette before it leaves the editor. */
    override fun handleBackPressed(): Boolean {
        if (binding.llPalette.visibility == View.VISIBLE) {
            binding.llPalette.visibility = View.GONE
            return true
        }
        return false
    }

    /** Paints the colour of the next stroke into the colour bar. */
    private fun showSwatch(colour: Int) {
        binding.swatch.colors = listOf(colour)
        binding.swatch.selectedIndex = 0
        binding.swatch.swatchDiameter = dp(10f)
        // The design shows the colour inside a thin light ring.
        binding.swatch.showSelectionRing = true
        binding.swatch.ringWidth = dp(2f)
        binding.swatch.ringColor = Color.parseColor("#d8d8d8")
    }

    private fun updateHistoryButtons() {
        binding.ivUndo.setImageResource(
            if (binding.canvas.canUndo) R.mipmap.ic_undo_enable else R.mipmap.ic_undo_unable,
        )
        binding.ivRedo.setImageResource(
            if (binding.canvas.canRedo) R.mipmap.ic_restore_enable else R.mipmap.ic_restore_unable,
        )
    }

    /**
     * Hands the signature back to the caller.
     *
     * The strokes are normalised against the box of the signature (the ink
     * without its empty margins) so the host can place them on a page without
     * knowing the size of the canvas.
     */
    private fun confirm() {
        val strokes = binding.canvas.inkStrokes()
        if (strokes.isEmpty()) {
            finish()
            return
        }
        val canvasWidth = binding.canvas.width.toFloat().coerceAtLeast(1f)
        val canvasHeight = binding.canvas.height.toFloat().coerceAtLeast(1f)
        val bounds = binding.canvas.inkBounds()
        val left = bounds.left * canvasWidth
        val top = bounds.top * canvasHeight
        val boxWidth = ((bounds.right - bounds.left) * canvasWidth).coerceAtLeast(1f)
        val boxHeight = ((bounds.bottom - bounds.top) * canvasHeight).coerceAtLeast(1f)

        val unit = strokes.map { stroke ->
            normalise(stroke, left, top, boxWidth, boxHeight, canvasWidth, canvasHeight)
        }
        val data = Intent()
            .putExtra(EXTRA_INK, AnnotationPayload.encodeInk(unit))
            .putExtra(EXTRA_ASPECT, boxWidth / boxHeight)
        setResult(RESULT_OK, data)
        finish()
    }

    private fun normalise(
        stroke: InkStroke,
        left: Float,
        top: Float,
        boxWidth: Float,
        boxHeight: Float,
        canvasWidth: Float,
        canvasHeight: Float,
    ): InkStroke {
        val points = FloatArray(stroke.points.size)
        var index = 0
        while (index + 1 < stroke.points.size) {
            points[index] = (stroke.points[index] * canvasWidth - left) / boxWidth
            points[index + 1] = (stroke.points[index + 1] * canvasHeight - top) / boxHeight
            index += 2
        }
        // The width of a stroke is a fraction of the box of the signature.
        return InkStroke(stroke.color, stroke.width * canvasWidth / boxWidth, points)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        /** The payload of the signature. */
        const val EXTRA_INK = "com.peng.pdfreader.extra.SIGNATURE_INK"

        /** The width / height ratio of the signature. */
        const val EXTRA_ASPECT = "com.peng.pdfreader.extra.SIGNATURE_ASPECT"

        private val DEFAULT_COLOUR = Color.parseColor("#333333")

        /** The intent that opens the editor. */
        fun createIntent(context: Context): Intent = Intent(context, SignatureActivity::class.java)
    }
}
