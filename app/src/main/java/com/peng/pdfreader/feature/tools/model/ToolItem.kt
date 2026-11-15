package com.peng.pdfreader.feature.tools.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.peng.pdfreader.R

/**
 * What a tool card of the tools grid does.
 */
enum class ToolAction {
    MERGE,
    SPLIT,
    IMAGE_TO_PDF,
    SCAN,
    LOCK,
    UNLOCK,
    PRINT,
    ZIP,
}

/**
 * One card of the tools grid.
 *
 * @param action what the card starts
 * @param labelRes label of the card
 * @param iconRes the exported artwork of the card (80x44)
 */
data class ToolItem(
    val action: ToolAction,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
)

/**
 * The catalogue of the tools tab.
 *
 * The order and the icons follow the design "tools": the grid is read row by
 * row, the artwork of every card is the exported icon of the design.
 */
object ToolCatalog {

    val items: List<ToolItem> = listOf(
        ToolItem(ToolAction.IMAGE_TO_PDF, R.string.image_to_pdf, R.mipmap.ic_tools_image_to_pdf),
        ToolItem(ToolAction.MERGE, R.string.merge, R.mipmap.ic_tools_merge_pdf),
        ToolItem(ToolAction.SPLIT, R.string.split, R.mipmap.ic_tools_split_pdf),
        ToolItem(ToolAction.SCAN, R.string.tools_scan_to_pdf, R.mipmap.ic_tools_scan),
        ToolItem(ToolAction.LOCK, R.string.encrypt, R.mipmap.ic_tools_lock),
        ToolItem(ToolAction.UNLOCK, R.string.decrypt, R.mipmap.ic_tools_unlock),
        ToolItem(ToolAction.PRINT, R.string.print, R.mipmap.ic_tools_print_pdf),
        ToolItem(ToolAction.ZIP, R.string.tools_zip_file, R.mipmap.ic_tools_compress),
    )
}
