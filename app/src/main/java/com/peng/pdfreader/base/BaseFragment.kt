package com.peng.pdfreader.base

import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.peng.pdfreader.R

/**
 * Base fragment of the app.
 *
 * It only carries page level plumbing that every page needs - most notably the
 * back key interception and the inset of the system bars. There is deliberately
 * **no UI interaction logic** in here: no title bar, no dialogs, no list
 * handling. Those live in the pages and in the shared widgets of the core
 * package.
 */
abstract class BaseFragment : Fragment() {

    /** The insets of the system bars of the page. */
    private val insets = PageInsets()

    override fun onStart() {
        super.onStart()
        // 页面在状态栏和导航栏的外面，具体的处理见 applyInsets()。
        applyInsets()
        val dispatcher = requireActivity().onBackPressedDispatcher
        // Tied to the view lifecycle, the callback is removed automatically
        // once the view of this fragment goes away.
        dispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!handleBackPressed()) {
                        isEnabled = false
                        dispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            },
        )
    }

    /**
     * 页面自己处理状态栏的 inset。
     *
     * 有标题栏的页面把状态栏的高度算进标题栏，没有的页面把 inset 加在页面
     * 根布局上。
     */
    protected open val takesStatusBarInset: Boolean = true

    /**
     * 页面自己处理导航栏的 inset。
     *
     * 有底部工具栏的页面把 inset 加在工具栏上，没有的页面把 inset 加在页面
     * 根布局上。壳的标签页在底部导航栏上面，由底部导航栏处理。
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
        val root = view ?: return
        if (!takesStatusBarInset && !takesBottomInset) return
        val topBar = if (takesStatusBarInset) root.findViewById<View>(R.id.topBar) else null
        val bottomBar = if (takesBottomInset) root.findViewById<View>(R.id.bottomBar) else null
        insets.apply(root, topBar, bottomBar, takesStatusBarInset, takesBottomInset)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 这个 fragment 的界面已经销毁，下一个界面从头开始。
        insets.reset()
    }

    /**
     * Back key interception.
     *
     * @return true when the page consumed the event, false to fall through to
     * the default behaviour.
     */
    protected open fun handleBackPressed(): Boolean = false
}
