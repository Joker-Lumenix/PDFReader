package com.peng.pdfreader.ui.dialogs

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.peng.pdfreader.BuildConfig
import com.peng.pdfreader.R
import com.peng.pdfreader.core.util.Logger
import com.peng.pdfreader.base.BaseBottomSheetDialog
import com.peng.pdfreader.databinding.SettingsRatingSheetBinding
import com.peng.pdfreader.ui.activities.FeedbackActivity

/**
 * Rating sheet of the design.
 *
 * The sheet floats above the app: the illustration peeks over the top edge of
 * the sheet, the question, the "top rating" bubble and the five stars follow.
 *
 * Five stars can be picked. Up to three stars send the user to the feedback
 * page, four and five stars open the store listing.
 */
class BottomSheetRating : BaseBottomSheetDialog() {

    /**
     * The illustration of the design stands on the scrim of the page, so the
     * sheet has no surface of its own.
     */
    override val hasSurface: Boolean = false

    private var _binding: SettingsRatingSheetBinding? = null
    private val binding get() = _binding!!

    /** The number of stars the user picked, 0 when nothing is picked yet. */
    private var rating = 0

    override fun createContentView(inflater: LayoutInflater, container: ViewGroup?): View {
        _binding = SettingsRatingSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // The illustration of the design stands on the scrim of the page, so
        // the surface of the sheet is only drawn by the sheet itself.
        sheetContainer?.background = ColorDrawable(Color.TRANSPARENT)

        applyTitle()
        applyHalos()
        applyStars()
        applyIllustration()
        updateAction()

        binding.btnAction.setOnClickListener { onActionClicked() }
    }

    override fun onStart() {
        super.onStart()
        // The scrim of the design is a 75 % black layer over the page.
        dialog?.window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(SCRIM_ALPHA)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** The question of the design: one line, 20sp medium, #343a63. */
    private fun applyTitle() {
        binding.tvTitle.text = getString(R.string.rating_question)
    }

    /** The two halos behind the fifth star of the design. */
    private fun applyHalos() {
        binding.vStarHalo.background = oval(R.color.scrim_20_peach)
        binding.vStarCore.background = oval(R.color.scrim_50_peach)
    }

    private fun oval(colorRes: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(ContextCompat.getColor(requireContext(), colorRes))
    }

    private fun applyStars() {
        val stars = listOf(
            binding.ivStar1,
            binding.ivStar2,
            binding.ivStar3,
            binding.ivStar4,
            binding.ivStar5,
        )
        stars.forEachIndexed { index, star ->
            val value = index + 1
            // The fifth star stays highlighted in every state of the design -
            // the bubble of the sheet points at it.
            val highlighted = value == 5 || (rating > 0 && value <= rating)
            star.setImageResource(
                if (highlighted) R.mipmap.ic_star_select else R.mipmap.ic_star_unselect,
            )
            star.setOnClickListener { onStarClicked(value) }
        }
    }

    private fun onStarClicked(value: Int) {
        rating = value
        applyStars()
        applyIllustration()
        updateAction()
    }

    /** A low rating shows the unhappy illustration of the design. */
    private fun applyIllustration() {
        binding.ivIllustration.setImageResource(
            if (rating in 1..3) R.mipmap.bg_rate_cloud else R.mipmap.bg_rate,
        )
    }

    private fun updateAction() {
        val button = binding.btnAction
        when {
            rating == 0 -> {
                button.isEnabled = false
                button.setBackgroundResource(R.drawable.bg_btn_unable_r16)
                button.setText(R.string.rate)
            }

            rating <= 3 -> {
                button.isEnabled = true
                button.setBackgroundResource(R.drawable.gradient_red)
                button.setText(R.string.feedback)
            }

            else -> {
                button.isEnabled = true
                button.setBackgroundResource(R.drawable.gradient_red)
                button.setText(R.string.rate_on_play)
            }
        }
    }

    private fun onActionClicked() {
        when {
            rating == 0 -> Unit
            rating <= 3 -> {
                startActivity(Intent(requireContext(), FeedbackActivity::class.java))
                dismissAllowingStateLoss()
            }

            else -> {
                openStore()
                dismissAllowingStateLoss()
            }
        }
    }

    private fun openStore() {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${BuildConfig.APPLICATION_ID}"),
        )
        try {
            startActivity(marketIntent)
            return
        } catch (error: ActivityNotFoundException) {
            Logger.w(TAG, "no store app installed, falling back to the web listing")
        }

        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"),
        )
        try {
            startActivity(webIntent)
        } catch (error: ActivityNotFoundException) {
            Logger.e(TAG, "no browser installed", error)
            Toast.makeText(requireContext(), R.string.rating_store_missing, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "BottomSheetRating"

        /** The scrim of the design: 75 % black. */
        private const val SCRIM_ALPHA = 0.75f

        /** Opens the rating sheet. */
        fun show(manager: FragmentManager) {
            BottomSheetRating().show(manager, TAG)
        }
    }
}
