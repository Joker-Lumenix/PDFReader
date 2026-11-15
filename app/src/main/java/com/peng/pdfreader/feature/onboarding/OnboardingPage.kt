package com.peng.pdfreader.feature.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.peng.pdfreader.R

/** One page of the onboarding. */
data class OnboardingPage(
    @DrawableRes val imageRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
)

/** The three pages of the onboarding. */
object OnboardingPages {

    val all: List<OnboardingPage> = listOf(
        OnboardingPage(R.mipmap.bg_guide_1, R.string.image_to_pdf, R.string.one_click_conversion),
        OnboardingPage(R.mipmap.bg_guide_2, R.string.one_stop_management, R.string.various_pdf_tools),
        OnboardingPage(R.mipmap.bg_guide_3, R.string.read_edit_scan_results, R.string.crop_draw_filter),
    )
}
