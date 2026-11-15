package com.peng.pdfreader.appcore.pdf

/**
 * A single entry of the PDF document outline (bookmark tree).
 *
 * @param title entry caption
 * @param page 1-based page the entry points at
 * @param level nesting depth, 0 for top level entries
 */
data class OutlineNode(
    val title: String,
    val page: Int,
    val level: Int = 0,
)
