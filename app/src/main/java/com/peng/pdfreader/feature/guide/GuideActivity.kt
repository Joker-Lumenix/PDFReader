package com.peng.pdfreader.feature.guide

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
import com.peng.pdfreader.appcore.util.SystemBarInsets
import com.peng.pdfreader.base.BaseActivity
import com.peng.pdfreader.databinding.GuideActivityBinding
import com.peng.pdfreader.data.prefs.AppPrefs
import com.peng.pdfreader.ui.permission.PermissionActivity

/**
 * The guide of the first start.
 *
 * It is a page of its own: the pager shows the three illustrations of the
 * design, the image fills the whole width of the screen and the title and the
 * subtitle sit underneath it.
 *
 * When the guide is finished it closes itself and the flow continues with the
 * page that asks for the access to the files.
 */
class GuideActivity : BaseActivity() {

    private lateinit var binding: GuideActivityBinding

    private val pages = GuidePages.all

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = GuideActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyNavigationBarInset()

        binding.pager.adapter = GuidePageAdapter(pages)
        binding.pager.registerOnPageChangeCallback(pageChangeCallback)

        buildPageIndicator()

        binding.btnNext.setOnClickListener { onNextClicked() }
    }

    /**
     * Keeps the next button above the navigation bar.
     *
     * The app draws edge to edge, so the window reaches behind the navigation
     * bar; the button takes its inset as padding.
     */
    private fun applyNavigationBarInset() {
        binding.root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(0, 0, 0, SystemBarInsets.bottom(insets))
            insets
        }
        binding.root.requestApplyInsets()
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
        finishGuide()
    }

    /**
     * Leaves the guide.
     *
     * The page closes itself and hands over to the permission page, which asks
     * for the access to the files.
     */
    private fun finishGuide() {
        AppPrefs.get(this).guideShown = true
        try {
            PermissionActivity.startAsFlowStep(this)
        } catch (error: ActivityNotFoundException) {
            Logger.w(TAG, "the permission page is not registered in the manifest")
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
                setColor(ContextCompat.getColor(this@GuideActivity, R.color.accent_rose))
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
        private const val TAG = "GuideActivity"
        private const val DOT_ACTIVE_WIDTH_DP = 24
        private const val DOT_SIZE_DP = 6
        private const val DOT_GAP_DP = 8

        /** Whether the guide has already been seen. */
        fun hasBeenShown(context: android.content.Context): Boolean =
            AppPrefs.get(context).guideShown

        /** Opens the guide. */
        fun start(context: android.content.Context) {
            context.startActivity(Intent(context, GuideActivity::class.java))
        }
    }
}
