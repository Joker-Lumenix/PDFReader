package com.peng.pdfreader.base

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import com.peng.pdfreader.R
import com.peng.pdfreader.core.util.SystemBarInsets

/**
 * Base activity of the app.
 *
 * It only carries page level plumbing that every page needs - most notably the
 * back key interception and the inset of the status bar. There is deliberately
 * **no UI interaction logic** in here: no title bar, no dialogs, no list
 * handling. Those live in the pages and in the shared widgets of the core
 * package.
 */
abstract class BaseActivity : AppCompatActivity() {

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (!handleBackPressed()) {
                // Not consumed by the page - let the framework handle it.
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    /**
     * Keeps the top bar of the page below the status bar.
     *
     * From Android 15 on the app draws edge to edge, so the content of a page
     * would start behind the status bar. The top bar of the page takes the
     * inset as padding, its own background still reaches the status bar.
     */
    override fun setContentView(view: View?) {
        super.setContentView(view)
        applyTopInset()
    }

    override fun setContentView(view: View?, params: android.view.ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        applyTopInset()
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        applyTopInset()
    }

    /**
     * Whether the page takes the inset of the status bar itself.
     *
     * The shell of the app and the reader host fragments: their pages have a
     * top bar of their own and take the inset there, the shell must not move
     * its content a second time.
     */
    protected open val takesStatusBarInset: Boolean = true

    /** Moves the top bar of the page below the status bar. */
    protected fun applyTopInset() {
        if (!takesStatusBarInset) return
        // A page with a top bar takes the inset there, its own background still
        // reaches the status bar. A page without one takes it at its root.
        val content = findViewById<ViewGroup>(android.R.id.content)
        val bar = findViewById<View>(R.id.topBar)
        val target = bar ?: (content?.getChildAt(0) as? View) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(target) { view, insets ->
            val top = SystemBarInsets.top(insets)
            if (bar != null) {
                // The bar of the design is 63dp tall, the status bar sits above
                // it: the bar grows by the inset, its content keeps its place
                // in the design.
                if (baseHeight == 0) baseHeight = view.layoutParams.height
                val params = view.layoutParams
                if (params.height != baseHeight + top) {
                    params.height = baseHeight + top
                    view.layoutParams = params
                }
                // The content of the bar is centred in the bar, so it has to
                // move by half of the inset - a RelativeLayout centres its
                // children without the padding of the bar, a LinearLayout with
                // them.
                if (view is ViewGroup) {
                    for (index in 0 until view.childCount) {
                        view.getChildAt(index).translationY = top / 2f
                    }
                }
            } else {
                view.updatePadding(top = top)
            }
            insets
        }
        ViewCompat.requestApplyInsets(target)
    }

    /** The height of the top bar without the inset of the status bar. */
    private var baseHeight = 0

    /**
     * Back key interception.
     *
     * @return true when the page consumed the event, false to fall through to
     * the default behaviour.
     */
    protected open fun handleBackPressed(): Boolean = false

    fun buildTitle(): CharSequence {
        val raw = getString(R.string.app_name)
        val marker = "PDF"
        val start = raw.indexOf(marker)
        if (start < 0) return raw
        return SpannableString(raw).apply {
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this@BaseActivity, R.color.brand_primary)),
                start,
                raw.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
}
