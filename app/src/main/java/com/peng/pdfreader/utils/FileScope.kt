package com.peng.pdfreader.utils

/**
 * Scope of the file list - the three tabs of the design.
 *
 * The tabs narrow the sandbox down; they are independent of the type filter
 * and of the sort order.
 */
enum class FileScope {
    /** Every document of the sandbox. */
    ALL,

    /** The documents that have been opened at least once. */
    RECENT,

    /** The documents that are marked as favourite. */
    FAVORITES,
}
