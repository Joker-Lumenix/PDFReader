package com.peng.pdfreader.feature.settings.widget

import android.content.Context
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.widget.AdContent

/**
 * The sample content of the ad slots.
 *
 * The app does not bundle an ad sdk, so the slots show a local sample. The
 * sample carries the same parts as the designs: the icon, the title, the
 * description, the badge and the action.
 */
object AdSamples {

    /** The badge of the ads, the designs mark them with "AD". */
    const val BADGE = "AD"

    /** The sample of the banner (360x72). */
    fun banner(context: Context): AdContent = AdContent(
        iconRes = R.mipmap.ic_launcher,
        title = context.getString(R.string.app_name),
        description = context.getString(R.string.offline_badge),
        badge = BADGE,
        actionText = context.getString(R.string.install),
    )

    /** The sample of the card (312x118). */
    fun card(context: Context): AdContent = AdContent(
        iconRes = R.mipmap.ic_launcher,
        title = context.getString(R.string.app_name),
        description = context.getString(R.string.offline_badge),
        badge = BADGE,
        actionText = context.getString(R.string.install),
    )
}
