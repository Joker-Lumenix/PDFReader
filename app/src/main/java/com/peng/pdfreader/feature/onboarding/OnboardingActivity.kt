package com.peng.pdfreader.feature.onboarding

import android.content.ActivityNotFoundException
import android.content.Intent
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
import com.peng.pdfreader.feature.settings.rating.RatingActivity

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

        binding.tvSkip.setOnClickListener { finishOnboarding() }
        binding.btnNext.setOnClickListener { onNextClicked() }

        updateNextLabel()
    }

    override fun onDestroy() {
        binding.pager.unregisterOnPageChangeCallback(pageChangeCallback)
        super.onDestroy()
    }

    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            updatePageIndicator(position)
            updateNextLabel()
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
        try {
            startActivity(Intent(this, RatingActivity::class.java))
        } catch (error: ActivityNotFoundException) {
            Logger.w(TAG, "the rating page is not registered in the manifest")
        }
        finish()
    }

    private fun updateNextLabel() {
        val isLast = binding.pager.currentItem >= pages.lastIndex
        binding.btnNext.setText(if (isLast) R.string.onboarding_start else R.string.next)
    }

    private fun buildPageIndicator() {
        val container = binding.llDots
        container.removeAllViews()
        pages.indices.forEach { index ->
            val dot = View(this)
            val size = (if (index == 0) DOT_ACTIVE_DP else DOT_INACTIVE_DP) * resources.displayMetrics.density
            dot.layoutParams = LinearLayout.LayoutParams(size.toInt(), size.toInt()).apply {
                marginStart = (DOT_GAP_DP * resources.displayMetrics.density).toInt()
            }
            dot.background = dotDrawable(index == 0)
            container.addView(dot)
        }
    }

    private fun updatePageIndicator(selected: Int) {
        val container = binding.llDots
        for (index in 0 until container.childCount) {
            val dot = container.getChildAt(index)
            dot.background = dotDrawable(index == selected)
            val size = ((if (index == selected) DOT_ACTIVE_DP else DOT_INACTIVE_DP) *
                resources.displayMetrics.density).toInt()
            dot.layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginStart = (DOT_GAP_DP * resources.displayMetrics.density).toInt()
            }
        }
    }

    private fun dotDrawable(active: Boolean): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(
                ContextCompat.getColor(
                    this@OnboardingActivity,
                    if (active) R.color.brand_primary else R.color.surface_border_strong,
                ),
            )
            setSize((DOT_ACTIVE_DP * density).toInt(), (DOT_ACTIVE_DP * density).toInt())
        }
    }

    private companion object {
        const val TAG = "OnboardingActivity"
        const val DOT_ACTIVE_DP = 8
        const val DOT_INACTIVE_DP = 8
        const val DOT_GAP_DP = 6
    }
}
