package com.peng.pdfreader.feature.guide

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.peng.pdfreader.R

/**
 * One page of the guide.
 *
 * The image fills the whole width of the screen, the title and the subtitle
 * sit underneath it (design: 引导1 / 引导2 / 引导3).
 */
data class GuidePage(
    @DrawableRes val imageRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
)

/** The three pages of the guide. */
object GuidePages {

    val all: List<GuidePage> = listOf(
        GuidePage(
            R.mipmap.bg_guide_1,
            R.string.image_to_pdf,
            R.string.one_click_conversion,
        ),
        GuidePage(
            R.mipmap.bg_guide_2,
            R.string.one_stop_management,
            R.string.various_pdf_tools,
        ),
        GuidePage(
            R.mipmap.bg_guide_3,
            R.string.read_edit_scan_results,
            R.string.crop_draw_filter,
        ),
    )
}
