package com.peng.pdfreader.feature.home.model

import androidx.annotation.ColorRes
import com.peng.pdfreader.R
import com.peng.pdfreader.data.model.DocType

/**
 * Colour of the file type chip of a document row.
 *
 * The design uses a dedicated colour per format. Word is the only one without
 * a token in the palette, so it falls back to the dark brand red.
 */
@ColorRes
fun DocType.chipColorRes(): Int = when (this) {
    DocType.PDF -> R.color.brand_primary_light
    DocType.WORD -> R.color.brand_primary_dark
    DocType.EXCEL -> R.color.text_success
    DocType.PPT -> R.color.status_warning
    DocType.IMAGE -> R.color.brand_primary_light
    DocType.TXT -> R.color.text_secondary
    DocType.OTHER -> R.color.text_secondary
}
