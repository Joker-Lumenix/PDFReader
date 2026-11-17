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
        // The page is below the status bar, see applyTopInset().
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
     * Moves the top bar of the page below the status bar.
     *
     * From Android 15 on the app draws edge to edge, so the content of a page
     * would start behind the status bar. The top bar of the page takes the
     * inset as padding, its own background still reaches the status bar.
     */
    protected fun applyTopInset() {
        val root = view ?: return
        // A page with a top bar takes the inset there, its own background still
        // reaches the status bar. A page without one takes it at its root.
        val bar = root.findViewById<View>(R.id.topBar)
        val target = bar ?: root
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

    override fun onDestroyView() {
        super.onDestroyView()
        // The view of the fragment is gone, the next one starts from scratch.
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
