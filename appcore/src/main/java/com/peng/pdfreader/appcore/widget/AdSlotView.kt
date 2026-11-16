package com.peng.pdfreader.appcore.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.peng.pdfreader.appcore.R

/**
 * An ad slot of the design.
 *
 * The app does not bundle an ad sdk, so the slot only reserves the space and
 * renders the local sample artwork. The two layouts of the design are the
 * banner (360x72) and the card (312x118).
 */
class AdSlotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    /** The two ad layouts of the design. */
    enum class Style {
        /** 360x72, used at the bottom of the tools page and inside the lists. */
        BANNER,

        /** 312x118, used inside the exit retention sheet. */
        CARD,
    }

    private var iconView: ImageView? = null
    private var titleView: TextView? = null
    private var descriptionView: TextView? = null
    private var badgeView: TextView? = null
    private var actionView: TextView? = null

    /** The layout that is shown inside the slot. */
    var style: Style = Style.BANNER
        set(value) {
            field = value
            inflateStyle()
        }

    /** The sample that is rendered inside the slot. */
    var content: AdContent? = null
        set(value) {
            field = value
            bind()
        }

    init {
        inflateStyle()
    }

    private fun inflateStyle() {
        removeAllViews()
        val layout = when (style) {
            Style.BANNER -> R.layout.widget_ad_banner
            Style.CARD -> R.layout.widget_ad_card
        }
        LayoutInflater.from(context).inflate(layout, this, true)
        iconView = findViewById(R.id.ivIcon)
        titleView = findViewById(R.id.tvTitle)
        descriptionView = findViewById(R.id.tvDescription)
        badgeView = findViewById(R.id.tvBadge)
        actionView = findViewById(R.id.tvAction)
        bind()
    }

    private fun bind() {
        val sample = content ?: return
        iconView?.setImageResource(sample.iconRes)
        titleView?.text = sample.title
        descriptionView?.text = sample.description
        badgeView?.text = sample.badge
        actionView?.text = sample.actionText
    }
}

/**
 * The content of an ad slot.
 *
 * @param iconRes the app icon of the sample
 * @param title the title of the sample
 * @param description the secondary line of the sample
 * @param badge the badge of the sample, "AD" in the design
 * @param actionText the label of the action button
 */
data class AdContent(
    val iconRes: Int,
    val title: String,
    val description: String,
    val badge: String,
    val actionText: String,
)
