package com.peng.pdfreader.base

import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.peng.pdfreader.R
import com.peng.pdfreader.core.util.SystemBarInsets

/**
 * Base fragment of the app.
 *
 * It only carries page level plumbing that every page needs - most notably the
 * back key interception and the inset of the status bar. There is deliberately
 * **no UI interaction logic** in here: no title bar, no dialogs, no list
 * handling. Those live in the pages and in the shared widgets of the core
 * package.
 */
abstract class BaseFragment : Fragment() {

    override fun onStart() {
        super.onStart()
               // 页面在状态栏下面，具体的处理见 applyTopInset()。
        applyTopInset()
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
     * 把页面的顶部标题栏移到状态栏下面。
     *
     * 从 Android 15 开始应用是全屏绘制的，页面的内容会顶到状态栏下面。
     * 有标题栏的页面把状态栏的高度算进标题栏，标题栏自己的背景仍然铺满
     * 状态栏那一条，状态栏和标题栏因此是同一个颜色。
     */
    protected fun applyTopInset() {
        val root = view ?: return
        // 有标题栏的页面把 inset 加在标题栏上，标题栏的背景仍然铺到状态栏；
        // 没有标题栏的页面把 inset 加在页面根布局上。
        val bar = root.findViewById<View>(R.id.topBar)
        val target = bar ?: root
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

    override fun onDestroyView() {
        super.onDestroyView()
        // 这个 fragment 的界面已经销毁，下一个界面从头开始。
        baseHeight = 0
    }

    /**
     * Back key interception.
     *
     * @return true when the page consumed the event, false to fall through to
     * the default behaviour.
     */
    protected open fun handleBackPressed(): Boolean = false
}
