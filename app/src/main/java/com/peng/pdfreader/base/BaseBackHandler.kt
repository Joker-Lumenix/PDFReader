package com.peng.pdfreader.base

/**
 * Implemented by pages that want to handle the back gesture themselves.
 *
 * The shell asks the visible page before falling back to the default
 * behaviour.
 */
interface BaseBackHandler {
    /** @return true when the page consumed the back gesture. */
    fun onBackHandled(): Boolean
}
