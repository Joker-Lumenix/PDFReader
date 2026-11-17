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
import com.peng.pdfreader.R

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

    /** The insets of the system bars of the page. */
    private val insets = PageInsets()

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
     * 页面的内容设置好之后，把顶部标题栏移到状态栏下面，
     * 把底部工具栏移到导航栏上面。
     *
     * 从 Android 15 开始应用是全屏绘制的，页面的内容会顶到状态栏和导航栏下面。
     */
    override fun setContentView(view: View?) {
        super.setContentView(view)
        applyInsets()
    }

    override fun setContentView(view: View?, params: android.view.ViewGroup.LayoutParams?) {
        super.setContentView(view, params)
        applyInsets()
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        applyInsets()
    }

    /**
     * 页面自己处理状态栏的 inset。
     *
     * 应用的壳和阅读器都是承载 fragment 的：它们的页面都有自己的顶部标题栏，
     * inset 在各自的标题栏上处理，壳不能把内容再挪一次。
     */
    protected open val takesStatusBarInset: Boolean = true

    /**
     * 页面自己处理导航栏的 inset。
     *
     * 有底部工具栏的页面把 inset 加在工具栏上，没有的页面把 inset 加在页面
     * 根布局上。承载 fragment 的页面由 fragment 处理，壳由底部导航栏处理。
     */
    protected open val takesBottomInset: Boolean = true

    /**
     * 把页面移到系统栏的下面。
     *
     * 从 Android 15 开始应用是全屏绘制的，页面的内容会顶到状态栏和导航栏下面。
     * 有标题栏的页面把状态栏的高度算进标题栏，标题栏自己的背景仍然铺满
     * 状态栏那一条，状态栏和标题栏因此是同一个颜色。底部工具栏拿导航栏的
     * 高度的方式是一样的。
     */
    protected fun applyInsets() {
        if (!takesStatusBarInset && !takesBottomInset) return
        val content = findViewById<ViewGroup>(android.R.id.content)
        val root = content?.getChildAt(0) as? View ?: return
        val topBar = if (takesStatusBarInset) findViewById<View>(R.id.topBar) else null
        val bottomBar = if (takesBottomInset) findViewById<View>(R.id.bottomBar) else null
        insets.apply(root, topBar, bottomBar, takesStatusBarInset, takesBottomInset)
    }

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
