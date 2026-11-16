package com.peng.pdfreader.utils

import com.peng.pdfreader.data.FileSort

/**
 * The sort options of the design (files-选择).
 *
 * The design offers both directions of three keys, the repository can only
 * sort in one direction per key - [reverse] tells whether the order of the
 * repository has to be flipped.
 */
enum class FileSortOption(val sort: FileSort, val reverse: Boolean) {
    /** New to old. */
    MODIFIED_NEWEST(FileSort.MODIFIED, false),

    /** Old to new. */
    MODIFIED_OLDEST(FileSort.MODIFIED, true),

    /** A to Z. */
    NAME_ASC(FileSort.NAME, false),

    /** Z to A. */
    NAME_DESC(FileSort.NAME, true),

    /** Large to small. */
    SIZE_LARGEST(FileSort.SIZE, false),

    /** Small to large. */
    SIZE_SMALLEST(FileSort.SIZE, true),
    ;

    companion object {
        /** The option that matches a stored base sort. */
        fun of(sort: FileSort): FileSortOption = when (sort) {
            FileSort.MODIFIED -> MODIFIED_NEWEST
            FileSort.NAME -> NAME_ASC
            FileSort.SIZE -> SIZE_LARGEST
            FileSort.CREATED -> MODIFIED_NEWEST
        }
    }
}
