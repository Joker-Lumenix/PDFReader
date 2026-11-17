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
     * 页面的内容设置好之后，把顶部标题栏移到状态栏下面。
     *
     * 从 Android 15 开始应用是全屏绘制的，页面的内容会顶到状态栏下面。
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
     * 页面自己处理状态栏的 inset。
     *
     * 应用的壳和阅读器都是承载 fragment 的：它们的页面都有自己的顶部标题栏，
     * inset 在各自的标题栏上处理，壳不能把内容再挪一次。
     */
    protected open val takesStatusBarInset: Boolean = true

    /**
     * 把页面的顶部标题栏移到状态栏下面。
     *
     * 从 Android 15 开始应用是全屏绘制的，页面的内容会顶到状态栏下面。
     * 有标题栏的页面把状态栏的高度算进标题栏，标题栏自己的背景仍然铺满
     * 状态栏那一条，状态栏和标题栏因此是同一个颜色。
     */
    protected fun applyTopInset() {
        if (!takesStatusBarInset) return
        // 有标题栏的页面把 inset 加在标题栏上，标题栏的背景仍然铺到状态栏；
        // 没有标题栏的页面把 inset 加在页面根布局上。
        val content = findViewById<ViewGroup>(android.R.id.content)
        val bar = findViewById<View>(R.id.topBar)
        val target = bar ?: (content?.getChildAt(0) as? View) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(target) { view, insets ->
            val top = SystemBarInsets.top(insets)
            if (bar != null) {
                // 设计稿里的标题栏是 63dp 高，状态栏在它上面：标题栏按 inset
                // 变高，里面的内容仍然留在设计稿的位置上。
                if (baseHeight == 0) baseHeight = view.layoutParams.height
                val params = view.layoutParams
                if (params.height != baseHeight + top) {
                    params.height = baseHeight + top
                    view.layoutParams = params
                }
                // 标题栏里的内容是在标题栏里居中的，所以要整体下移半个 inset：
                // RelativeLayout 居中时不看标题栏的 padding，LinearLayout 看。
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

    /** 标题栏在没有状态栏 inset 时的高度。 */
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
