package com.peng.pdfreader.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.peng.pdfreader.R
import com.peng.pdfreader.ads.Ads
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.core.widget.AdSlotView
import com.peng.pdfreader.databinding.ActivityToolsProgressBinding
import com.peng.pdfreader.feature.tools.progress.ToolProgressBus
import com.peng.pdfreader.feature.tools.progress.ToolProgressState
import com.peng.pdfreader.feature.tools.util.ToolShapes
import com.peng.pdfreader.ui.uiData.AdSamples
import kotlinx.coroutines.launch

/**
 * The progress page of the tool flows.
 *
 * Every tool - image to pdf, merge, split, compress, scan - opens this page
 * while it builds a document. The page shows the progress of the job and opens
 * the result page as soon as the job is done.
 *
 * The page does not know the tool that opened it, it only shows the state of
 * the job.
 */
class ToolProgressActivity : BaseActivity() {

    private lateinit var binding: ActivityToolsProgressBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityToolsProgressBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // the design of the running job shows an empty app bar
        binding.tvTitle.text = ""
        binding.ivBack.visibility = View.GONE

        // the progress track of the design: a 240x6 track with a handle
        binding.progressView.progressTrack.background =
            ToolShapes.roundRect(this, TRACK_COLOR, 3f)
        binding.progressView.progressFill.background =
            ToolShapes.gradientPill(this, R.drawable.gradient_red, 16f)
        binding.progressView.progressKnob.setImageDrawable(ToolShapes.progressKnob(this))

        // the ad slot of the design, below the progress of the job
        binding.progressView.adSlot.style = AdSlotView.Style.CARD
        binding.progressView.adSlot.content = AdSamples.card(this)
        Ads.loadCard(binding.progressView.adSlot)

        observeJob()
    }

    override fun onDestroy() {
        super.onDestroy()
        ToolProgressBus.reset()
    }

    /** Follows the job of the tool that opened the page. */
    private fun observeJob() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ToolProgressBus.state.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: ToolProgressState) {
        binding.progressView.tvProgressPercent.text = "${state.percent}%"
        state.label?.let { binding.progressView.tvProgressLabel.text = it }

        val percent = state.percent
        val track = dp(TRACK_WIDTH_DP)
        val filled = (track * percent / 100).coerceAtLeast(if (percent > 0) dp(8) else 0)
        binding.progressView.progressFill.layoutParams =
            binding.progressView.progressFill.layoutParams.apply { width = filled }
        binding.progressView.progressKnob.translationX =
            (filled - dp(KNOB_SIZE_DP / 2)).toFloat()

        if (state.running) return

        // The job is done: the result page shows what it produced.
        val result = state.result
        if (result != null) {
            ResultActivity.start(this@ToolProgressActivity, result)
        }
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        /** Width of the progress track of the design. */
        private const val TRACK_WIDTH_DP = 240

        /** Size of the handle of the progress track. */
        private const val KNOB_SIZE_DP = 24

        /** Colour of the progress track of the design. */
        private const val TRACK_COLOR = 0xFFE1E1E1.toInt()

        /** Opens the progress page. */
        fun start(context: Context) {
            context.startActivity(Intent(context, ToolProgressActivity::class.java))
        }
    }
}
