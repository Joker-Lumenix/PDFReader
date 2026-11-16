package com.peng.pdfreader.feature.home.model

import androidx.annotation.ColorRes
import com.peng.pdfreader.R
import com.peng.pdfreader.data.model.DocType

/**
 * Colour of the file type chip of a document row.
 *
 * The design uses a dedicated colour per format: PDF is the light brand red,
 * Word the link blue, Excel the success green and PPT the warning amber.
 */
@ColorRes
fun DocType.chipColorRes(): Int = when (this) {
    DocType.PDF -> R.color.brand_primary_light
    DocType.WORD -> R.color.text_link
    DocType.EXCEL -> R.color.text_success
    DocType.PPT -> R.color.text_warning
    DocType.IMAGE -> R.color.brand_primary_light
    DocType.TXT -> R.color.text_secondary
    DocType.OTHER -> R.color.text_secondary
}
