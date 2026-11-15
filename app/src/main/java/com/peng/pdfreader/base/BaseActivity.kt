package com.peng.pdfreader.base

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * Base activity of the app.
 *
 * It only carries page level plumbing that every page needs - most notably the
 * back key interception. There is deliberately **no UI interaction logic** in
 * here: no title bar, no dialogs, no list handling. Those live in the pages
 * and in the shared widgets of `:appcore`.
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
     * Back key interception.
     *
     * @return true when the page consumed the event, false to fall through to
     * the default behaviour.
     */
    protected open fun handleBackPressed(): Boolean = false
}
