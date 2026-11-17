package com.peng.pdfreader.base

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import com.peng.pdfreader.core.util.SystemBarInsets

/**
 * The insets of the system bars of a page.
 *
 * The app draws edge to edge, so the content of a page reaches behind the
 * status bar and behind the navigation bar.
 *
 * The status bar belongs to the title bar of the page: the title bar grows by
 * the inset, so its background still covers the status bar and its content
 * stays at the position of the design. The navigation bar belongs to the
 * bottom bar of the page, the same way. A page without a bottom bar takes
 * the inset on its root, so nothing of the page ends up behind the navigation
 * bar.
 */
internal class PageInsets {

    /** The height of the title bar without the inset of the status bar. */
    private var topBarHeight = 0

    /** The height of the bottom bar without the inset of the navigation bar. */
    private var bottomBarHeight = 0

    /** The bottom padding of the bottom bar without the inset. */
    private var bottomBarPadding = 0
    private var bottomBarPaddingKnown = false

    /** The padding of the root without the insets of the system bars. */
    private var rootTopPadding = 0
    private var rootBottomPadding = 0
    private var rootPaddingKnown = false

    /**
     * Applies the insets of the system bars to the page.
     *
     * @param root the root layout of the page
     * @param topBar the title bar of the page, null when the page has none
     * @param bottomBar the bottom bar of the page, null when the page has none
     * @param takesTop whether the page takes the inset of the status bar
     * @param takesBottom whether the page takes the inset of the navigation bar
     */
    fun apply(
        root: View,
        topBar: View?,
        bottomBar: View?,
        takesTop: Boolean,
        takesBottom: Boolean,
    ) {
        // The root takes the insets that no bar of the page takes for itself.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            if (!rootPaddingKnown) {
                rootTopPadding = view.paddingTop
                rootBottomPadding = view.paddingBottom
                rootPaddingKnown = true
            }
            if (takesTop && topBar == null) {
                view.updatePadding(top = rootTopPadding + SystemBarInsets.top(insets))
            }
            if (takesBottom && bottomBar == null) {
                view.updatePadding(bottom = rootBottomPadding + SystemBarInsets.bottom(insets))
            }
            insets
        }

        topBar?.let { applyTopBar(it) }
        bottomBar?.let { applyBottomBar(it) }

        ViewCompat.requestApplyInsets(root)
    }

    /**
     * The status bar belongs to the title bar.
     *
     * The title bar grows by the inset, its background therefore still covers
     * the status bar. Its content stays at the position of the design, so it
     * is moved down by half of the inset.
     */
    private fun applyTopBar(bar: View) {
        ViewCompat.setOnApplyWindowInsetsListener(bar) { view, insets ->
            val top = SystemBarInsets.top(insets)
            val params = view.layoutParams
            if (params.height > 0) {
                if (topBarHeight == 0) topBarHeight = params.height
                if (params.height != topBarHeight + top) {
                    params.height = topBarHeight + top
                    view.layoutParams = params
                }
            } else {
                view.updatePadding(top = view.paddingTop + top)
            }
            // 标题栏里的内容是在标题栏里居中的，所以要整体下移半个 inset：
            // RelativeLayout 居中时不看标题栏的 padding，LinearLayout 看。
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) {
                    view.getChildAt(index).translationY = top / 2f
                }
            }
            insets
        }
        ViewCompat.requestApplyInsets(bar)
    }

    /**
     * The navigation bar belongs to the bottom bar.
     *
     * A bar with a fixed height grows by the inset, so its background still
     * covers the navigation bar and its content stays at the position of the
     * design. A bar that floats above the content has no height of its own,
     * it takes the inset as the padding below its content instead.
     */
    private fun applyBottomBar(bar: View) {
        ViewCompat.setOnApplyWindowInsetsListener(bar) { view, insets ->
            val bottom = SystemBarInsets.bottom(insets)
            val params = view.layoutParams
            if (params.height > 0) {
                if (bottomBarHeight == 0) bottomBarHeight = params.height
                if (params.height != bottomBarHeight + bottom) {
                    params.height = bottomBarHeight + bottom
                    view.layoutParams = params
                }
            } else {
                if (!bottomBarPaddingKnown) {
                    bottomBarPadding = view.paddingBottom
                    bottomBarPaddingKnown = true
                }
                view.updatePadding(bottom = bottomBarPadding + bottom)
            }
            insets
        }
        ViewCompat.requestApplyInsets(bar)
    }

    /**
     * Forgets the measurements of the bars.
     *
     * The view of a fragment is destroyed and built again when the page comes
     * back, the bars are measured again then.
     */
    fun reset() {
        topBarHeight = 0
        bottomBarHeight = 0
        bottomBarPadding = 0
        bottomBarPaddingKnown = false
        rootTopPadding = 0
        rootBottomPadding = 0
        rootPaddingKnown = false
    }
}
