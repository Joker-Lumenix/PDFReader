package com.peng.pdfreader.base

import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment

/**
 * Base fragment of the app.
 *
 * It only carries page level plumbing that every page needs - most notably the
 * back key interception. There is deliberately **no UI interaction logic** in
 * here: no title bar, no dialogs, no list handling. Those live in the pages
 * and in the shared widgets of `:appcore`.
 */
abstract class BaseFragment : Fragment() {

    override fun onStart() {
        super.onStart()
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
     * Back key interception.
     *
     * @return true when the page consumed the event, false to fall through to
     * the default behaviour.
     */
    protected open fun handleBackPressed(): Boolean = false
}
