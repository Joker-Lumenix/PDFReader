package com.peng.pdfreader.feature.preview.activity

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.widget.ColorPaletteView
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.databinding.ActivitySignatureBinding
import com.peng.pdfreader.feature.preview.annotation.AnnotationPayload

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

        binding.ivBack.setOnClickListener { finish() }
        binding.tvDone.setOnClickListener { confirm() }
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

        binding.llPalette.background = GradientDrawable().apply {
            cornerRadius = dp(16f)
            setColor(PALETTE_BACKGROUND)
        }
        binding.palette.colors = ColorPaletteView.DEFAULT_COLORS
        binding.palette.swatchDiameter = dp(20f)
        binding.palette.itemSpacing = dp(15f)
        binding.palette.selectedColor = DEFAULT_COLOUR
        binding.palette.onColorSelected = { colour ->
            binding.canvas.strokeColor = colour
            binding.tvColorSwatch.setTextColor(colour)
        }
        binding.tvColorSwatch.setTextColor(DEFAULT_COLOUR)

        val toggle = View.OnClickListener { togglePalette() }
        binding.tvColorLabel.setOnClickListener(toggle)
        binding.tvColorSwatch.setOnClickListener(toggle)

        updateHistoryButtons()
    }

    private fun togglePalette() {
        val palette = binding.llPalette
        palette.visibility = if (palette.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun updateHistoryButtons() {
        binding.ivUndo.setImageResource(
            if (binding.canvas.canUndo) R.mipmap.ic_undo_enable else R.mipmap.ic_undo_unable,
        )
        binding.ivRedo.alpha = if (binding.canvas.canRedo) 1f else 0.4f
    }

    private fun confirm() {
        val strokes = binding.canvas.inkStrokes()
        if (strokes.isEmpty()) {
            finish()
            return
        }
        val bounds = binding.canvas.inkBounds()
        val data = Intent()
            .putExtra(EXTRA_INK, AnnotationPayload.encodeInk(strokes))
            .putExtra(
                EXTRA_CANVAS,
                floatArrayOf(binding.canvas.width.toFloat(), binding.canvas.height.toFloat()),
            )
            .putExtra(
                EXTRA_BOUNDS,
                floatArrayOf(bounds.left, bounds.top, bounds.right, bounds.bottom),
            )
        setResult(RESULT_OK, data)
        finish()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        /** The payload of the signature. */
        const val EXTRA_INK = "com.peng.pdfreader.extra.SIGNATURE_INK"

        /** The size of the signing canvas. */
        const val EXTRA_CANVAS = "com.peng.pdfreader.extra.SIGNATURE_CANVAS"

        /** The bounding box of the signature inside the canvas. */
        const val EXTRA_BOUNDS = "com.peng.pdfreader.extra.SIGNATURE_BOUNDS"

        private val DEFAULT_COLOUR = Color.parseColor("#333333")
        private const val PALETTE_BACKGROUND = 0xCC313131.toInt()

        /** The intent that opens the editor. */
        fun createIntent(context: Context): Intent = Intent(context, SignatureActivity::class.java)
    }
}
