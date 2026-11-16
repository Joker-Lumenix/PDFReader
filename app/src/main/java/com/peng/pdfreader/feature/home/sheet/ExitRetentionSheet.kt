package com.peng.pdfreader.feature.home.sheet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.appcore.widget.AdSlotView
import com.peng.pdfreader.appcore.widget.BaseBottomSheetDialog
import com.peng.pdfreader.databinding.HomeSheetExitRetentionBinding
import com.peng.pdfreader.feature.settings.widget.AdSamples
import com.peng.pdfreader.feature.tools.activity.ImageToPdfActivity
import com.peng.pdfreader.feature.tools.activity.MergeActivity
import com.peng.pdfreader.feature.tools.activity.SplitActivity
import com.peng.pdfreader.feature.tools.scanner.ScanActivity

/**
 * The exit retention sheet (design: 退出挽留).
 *
 * The sheet is shown when the user leaves the app from the home tab. It offers
 * one of the tools of the app - the design picks a random one - and the ad
 * card of the design below it.
 */
class ExitRetentionSheet : BaseBottomSheetDialog() {

    private var _binding: HomeSheetExitRetentionBinding? = null
    private val binding get() = _binding!!

    /** Raised when the user leaves the app through the sheet. */
    var onExit: (() -> Unit)? = null

    /** Raised once the sheet is gone, whatever the user picked. */
    var onDismissed: (() -> Unit)? = null

    /** The tool that is offered to the user. */
    private val feature = OFFERS.random()

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = HomeSheetExitRetentionBinding.inflate(inflater, container, false)

        binding.ivCharacter.setImageResource(randomCharacter())
        binding.ivFeatureIcon.setImageResource(feature.iconRes)
        binding.tvFeatureLabel.setText(feature.labelRes)

        binding.adSlot.style = AdSlotView.Style.CARD
        binding.adSlot.content = AdSamples.card(requireContext())

        binding.featureCard.setOnClickListener { openFeature() }
        binding.btnTry.setOnClickListener { openFeature() }
        binding.btnExit.setOnClickListener {
            dismissAllowingStateLoss()
            onExit?.invoke()
        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onDismissed?.invoke()
    }

    /** Opens the offered tool. */
    private fun openFeature() {
        dismissAllowingStateLoss()
        val context = context ?: return
        try {
            feature.open(context)
        } catch (error: Exception) {
            Logger.e(TAG, "the offered tool could not be opened", error)
        }
    }

    /**
     * The face of the design is picked at random.
     *
     * The artwork of the design is not part of the repository yet, the three
     * expressions of the app stand in for it.
     */
    private fun randomCharacter(): Int = CHARACTERS.random()

    /**
     * One of the tools the sheet can offer.
     *
     * @param labelRes the name of the tool
     * @param iconRes the icon of the tool
     * @param open starts the tool
     */
    private data class Offer(
        val labelRes: Int,
        val iconRes: Int,
        val open: (Context) -> Unit,
    )

    companion object {
        private const val TAG = "ExitRetentionSheet"

        /** The faces the sheet can show. */
        private val CHARACTERS = intArrayOf(
            R.mipmap.emoji_normal,
            R.mipmap.emoji_laugh,
            R.mipmap.emoji_cry,
        )

        /** The tools the sheet offers. */
        private val OFFERS = listOf(
            Offer(
                R.string.image_to_pdf,
                R.mipmap.ic_tools_image_to_pdf,
            ) { context -> context.startActivity(Intent(context, ImageToPdfActivity::class.java)) },
            Offer(
                R.string.merge,
                R.mipmap.ic_tools_merge_pdf,
            ) { context -> MergeActivity.start(context) },
            Offer(
                R.string.scan_to_pdf,
                R.mipmap.ic_tools_scan,
            ) { context -> context.startActivity(Intent(context, ScanActivity::class.java)) },
            Offer(
                R.string.split,
                R.mipmap.ic_tools_split_pdf,
            ) { context -> SplitActivity.start(context) },
        )

        /** Shows the sheet. */
        fun show(manager: FragmentManager, onExit: () -> Unit, onDismissed: () -> Unit) {
            ExitRetentionSheet().apply {
                this.onExit = onExit
                this.onDismissed = onDismissed
            }.show(manager, TAG)
        }
    }
}
