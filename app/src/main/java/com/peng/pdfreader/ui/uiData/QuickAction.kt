package com.peng.pdfreader.ui.uiData

/**
 * The quick actions of the home tab.
 *
 * The fragment never decides what an action does, it only reports which card
 * was tapped so that the shell can route it.
 */
enum class QuickAction {
    /** "图片转 PDF" */
    IMAGE_TO_PDF,

    /** "PDF 合并" */
    MERGE_PDF,

    /** The narrow chevron card that opens the full tool list. */
    MORE,
}
