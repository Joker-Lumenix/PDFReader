package com.peng.pdfreader.feature.tools.view

import android.content.Context
import android.view.View
import com.peng.pdfreader.R
import com.peng.pdfreader.data.Document
import com.peng.pdfreader.databinding.ActivityResultBinding
import com.peng.pdfreader.feature.tools.util.ToolFormatters
import com.peng.pdfreader.feature.tools.util.ToolShapes

/**
 * The actions the result page can trigger.
 */
data class ToolsResultActions(
    val onOpenWith: () -> Unit,
    val onCompress: () -> Unit,
    val onPrint: () -> Unit,
    val onSign: () -> Unit,
    val onDraw: () -> Unit,
    val onLock: () -> Unit,
    val onShare: () -> Unit,
    val onView: () -> Unit,
    val onRename: () -> Unit,
    val onFeedback: (good: Boolean) -> Unit,
)

/**
 * Binds the shared result page (the merge success page and the compress
 * result page) to the document a tool has produced.
 */
object ToolsResultBinder {

    /** Shows the produced document on the result page. */
    fun bindDocument(
        context: Context,
        binding: ActivityResultBinding,
        document: Document,
    ) {
        binding.fileCard.background =
            ToolShapes.bottomRounded(context, SURFACE_CARD, 8f)
        binding.ivPreview.background = ToolShapes.roundRect(context, PREVIEW_TILE, 4f)
        binding.tvFileName.text = document.name
        binding.tvFilePath.text = context.getString(R.string.tools_result_path, document.path)
        binding.tvPageBadge.text = document.pageCount.toString()
        binding.tvPageBadge.background = ToolShapes.pageCountBadge(context)
    }

    /** Shows the success title of the merge flow. */
    fun bindSuccess(context: Context, binding: ActivityResultBinding, title: String) {
        binding.heroSuccess.visibility = View.VISIBLE
        binding.heroStats.visibility = View.GONE
        binding.tvHeroTitle.text = title
        binding.ivHeroMark.setImageResource(R.mipmap.ic_ok)
    }

    /** Shows the size comparison of the compress flow. */
    fun bindSizes(context: Context, binding: ActivityResultBinding, before: Long, after: Long) {
        binding.heroSuccess.visibility = View.GONE
        binding.heroStats.visibility = View.VISIBLE
        binding.tvSizeBefore.text = ToolFormatters.size(before)
        binding.tvSizeAfter.text = ToolFormatters.size(after)
        binding.tvBeforePill.text = context.getString(R.string.before)
        binding.tvAfterPill.text = context.getString(R.string.after)
        binding.tvBeforePill.background = ToolShapes.roundRect(context, ToolShapes.SIZE_BEFORE, 8f)
        binding.tvAfterPill.background = ToolShapes.roundRect(context, ToolShapes.SIZE_AFTER, 8f)
    }

    /** Wires every button of the result page. */
    fun bindActions(
        context: Context,
        binding: ActivityResultBinding,
        actions: ToolsResultActions,
    ) {
        // the two feedback buttons of the design are pills with a radius of 20
        binding.btnNotReally.background =
            ToolShapes.roundRect(context, SURFACE_NEUTRAL, 20f)
        binding.btnGood.background =
            ToolShapes.gradientPill(context, R.drawable.gradient_red, 20f)

        binding.cellOpenWith.setOnClickListener { actions.onOpenWith() }
        binding.cellCompress.setOnClickListener { actions.onCompress() }
        binding.cellPrint.setOnClickListener { actions.onPrint() }
        binding.cellSign.setOnClickListener { actions.onSign() }
        binding.cellDraw.setOnClickListener { actions.onDraw() }
        binding.cellLock.setOnClickListener { actions.onLock() }
        binding.btnShare.setOnClickListener { actions.onShare() }
        binding.btnView.setOnClickListener { actions.onView() }
        binding.btnRename.setOnClickListener { actions.onRename() }
        binding.btnGood.setOnClickListener { actions.onFeedback(true) }
        binding.btnNotReally.setOnClickListener { actions.onFeedback(false) }
        binding.btnCloseFeedback.setOnClickListener {
            binding.feedbackCard.visibility = View.GONE
        }
    }

    /** Colour of the card surface of the design. */
    private const val SURFACE_CARD = 0xFFFAFAFA.toInt()

    /** Colour of the preview placeholder of the design. */
    private const val PREVIEW_TILE = 0xFFFFCECE.toInt()

    /** Colour of the "not really" button of the design. */
    private const val SURFACE_NEUTRAL = 0xFFC4C4C4.toInt()
}
