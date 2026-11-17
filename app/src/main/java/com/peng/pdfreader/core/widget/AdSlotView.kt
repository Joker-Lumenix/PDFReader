package com.peng.pdfreader.core.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.peng.pdfreader.R

/**
 * An ad slot of the design.
 *
 * The slot reserves the space of the design and renders the local sample
 * artwork until a real ad arrives; the ad then takes the whole slot. The two
 * layouts of the design are the banner (360x72) and the card (312x118).
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

    /** The holder of the sample artwork of the design. */
    private val sampleHost = FrameLayout(context)

    /** The holder of the real ad. */
    private val adHost = FrameLayout(context)

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
        addView(
            sampleHost,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        addView(adHost, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        adHost.visibility = GONE
        inflateStyle()
    }

    private fun inflateStyle() {
        sampleHost.removeAllViews()
        val layout = when (style) {
            Style.BANNER -> R.layout.widget_ad_banner
            Style.CARD -> R.layout.widget_ad_card
        }
        LayoutInflater.from(context).inflate(layout, sampleHost, true)
        iconView = sampleHost.findViewById(R.id.ivIcon)
        titleView = sampleHost.findViewById(R.id.tvTitle)
        descriptionView = sampleHost.findViewById(R.id.tvDescription)
        badgeView = sampleHost.findViewById(R.id.tvBadge)
        actionView = sampleHost.findViewById(R.id.tvAction)
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

    /**
     * Puts a real ad into the slot.
     *
     * The sample artwork of the design is hidden, the ad takes its place.
     */
    fun setAdView(view: android.view.View) {
        adHost.removeAllViews()
        adHost.addView(
            view,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT),
        )
        adHost.visibility = VISIBLE
        sampleHost.visibility = GONE
    }

    /** Removes the real ad, the sample artwork of the design comes back. */
    fun clearAdView() {
        adHost.removeAllViews()
        adHost.visibility = GONE
        sampleHost.visibility = VISIBLE
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
