package com.peng.pdfreader.feature.files

/**
 * Contract between the file action sheet and the page that shows it.
 *
 * The sheet never touches the repository for anything but the favourite
 * toggle; every other operation is handed back to the page so that the
 * navigation, the dialogs and the list stay in one place.
 */
object FileActions {

    /** Fragment result key used by [com.peng.pdfreader.feature.files.sheet.FileActionSheet]. */
    const val REQUEST_KEY = "com.peng.pdfreader.files.ACTION"

    /** Key of the requested action inside the result bundle. */
    const val KEY_ACTION = "action"

    /** Key of the document id inside the result bundle. */
    const val KEY_DOC_ID = "docId"

    const val RENAME = "rename"
    const val OPEN_WITH = "open_with"
    const val SHARE = "share"
    const val PRINT = "print"
    const val SPLIT = "split"
    const val COMPRESS = "compress"
    const val MERGE = "merge"
    const val LOCK = "lock"
    const val DELETE = "delete"

    /** The favourite state of a document changed. */
    const val CHANGED = "changed"
}
