package com.peng.pdfreader.feature.settings.rating

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.peng.pdfreader.BuildConfig
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.databinding.SettingsRatingActivityBinding
import com.peng.pdfreader.feature.feedback.FeedbackActivity
import com.peng.pdfreader.feature.settings.util.SettingsScreen
import com.peng.pdfreader.ui.permission.PermissionActivity

/**
 * Rating sheet of the design.
 *
 * The sheet floats above the app: the window stays transparent, the layout
 * draws the 75% scrim, the illustration that peeks over the top edge of the
 * sheet and the sheet itself.
 *
 * Five stars can be picked. Up to three stars send the user to the feedback
 * page, four and five stars open the store listing.
 */
class RatingActivity : BaseActivity() {

    private lateinit var binding: SettingsRatingActivityBinding

    /** The number of stars the user picked, 0 when nothing is picked yet. */
    private var rating = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The sheet is drawn above the app, so the window must not paint a
        // background of its own.
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        binding = SettingsRatingActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyTitle()
        applyHalos()
        applyStars()
        applyIllustration()
        updateAction()

        binding.scrim.setOnClickListener { continueFlow() }
        binding.btnAction.setOnClickListener { onActionClicked() }
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
        setColor(ContextCompat.getColor(this@RatingActivity, colorRes))
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
                SettingsScreen.start(this, Intent(this, FeedbackActivity::class.java))
                continueFlow()
            }

            else -> {
                openStore()
                continueFlow()
            }
        }
    }

    /**
     * Leaves the page.
     *
     * During the start up flow the permission step follows, otherwise the page
     * simply closes.
     */
    private fun continueFlow() {
        if (intent.getBooleanExtra(EXTRA_CONTINUE_FLOW, false)) {
            PermissionActivity.startAsFlowStep(this)
        }
        finish()
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
            Toast.makeText(this, R.string.rating_store_missing, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "RatingActivity"

        /** Set when the page is a step of the start up flow. */
        const val EXTRA_CONTINUE_FLOW = "com.peng.pdfreader.extra.CONTINUE_FLOW"
    }
}
