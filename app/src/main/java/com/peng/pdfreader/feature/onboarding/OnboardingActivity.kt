package com.peng.pdfreader.feature.onboarding

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.util.Logger
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.databinding.OnboardingActivityBinding
import com.peng.pdfreader.data.prefs.AppPrefs
import com.peng.pdfreader.feature.settings.rating.RatingActivity
import com.peng.pdfreader.ui.permission.PermissionActivity

/**
 * The three page onboarding of the app.
 *
 * It is opened by [OnboardingLauncher] on the very first start and shows the
 * three illustrations of the design.
 */
class OnboardingActivity : BaseActivity() {

    private lateinit var binding: OnboardingActivityBinding

    private val pages = OnboardingPages.all

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = OnboardingActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.pager.adapter = OnboardingPageAdapter(pages)
        binding.pager.registerOnPageChangeCallback(pageChangeCallback)

        buildPageIndicator()

        binding.btnNext.setOnClickListener { onNextClicked() }
    }

    override fun onDestroy() {
        binding.pager.unregisterOnPageChangeCallback(pageChangeCallback)
        super.onDestroy()
    }

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            updatePageIndicator(position)
        }
    }

    private fun onNextClicked() {
        if (binding.pager.currentItem < pages.lastIndex) {
            binding.pager.currentItem = binding.pager.currentItem + 1
            return
        }
        finishOnboarding()
    }

    private fun finishOnboarding() {
        // The rating page follows the onboarding, as in the design.
        AppPrefs.get(this).onboardingShown = true
        try {
            startActivity(
                Intent(this, RatingActivity::class.java)
                    .putExtra(RatingActivity.EXTRA_CONTINUE_FLOW, true),
            )
        } catch (error: ActivityNotFoundException) {
            Logger.w(TAG, "the rating page is not registered in the manifest")
            PermissionActivity.startAsFlowStep(this)
        }
        finish()
    }

    private fun buildPageIndicator() {
        updatePageIndicator(binding.pager.currentItem)
    }

    /**
     * Rebuilds the page indicator.
     *
     * The design shows the current page as a 24x6dp pill and the other pages
     * as 6dp dots, 8dp apart from each other.
     */
    private fun updatePageIndicator(selected: Int) {
        val container = binding.llDots
        container.removeAllViews()
        pages.indices.forEach { index ->
            val active = index == selected
            val dot = View(this)
            dot.layoutParams = LinearLayout.LayoutParams(
                dp(if (active) DOT_ACTIVE_WIDTH_DP else DOT_SIZE_DP),
                dp(DOT_SIZE_DP),
            ).apply {
                marginStart = if (index == 0) 0 else dp(DOT_GAP_DP)
            }
            dot.background = dotDrawable(active)
            container.addView(dot)
        }
    }

    private fun dotDrawable(active: Boolean): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            if (active) {
                cornerRadius = (DOT_SIZE_DP * density) / 2f
                setColor(ContextCompat.getColor(this@OnboardingActivity, R.color.accent_rose))
                setSize((DOT_ACTIVE_WIDTH_DP * density).toInt(), (DOT_SIZE_DP * density).toInt())
            } else {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(127, 0xE3, 0xE3, 0xE3))
                setSize((DOT_SIZE_DP * density).toInt(), (DOT_SIZE_DP * density).toInt())
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "OnboardingActivity"
        private const val DOT_ACTIVE_WIDTH_DP = 24
        private const val DOT_SIZE_DP = 6
        private const val DOT_GAP_DP = 8

        /** Whether the onboarding has already been seen. */
        fun hasBeenShown(context: android.content.Context): Boolean =
            AppPrefs.get(context).onboardingShown
    }
}
