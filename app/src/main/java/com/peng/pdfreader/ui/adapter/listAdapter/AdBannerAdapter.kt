package com.peng.pdfreader.ui.adapter.listAdapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.ads.Ads
import com.peng.pdfreader.core.widget.AdContent
import com.peng.pdfreader.core.widget.AdSlotView
import com.peng.pdfreader.databinding.FilesItemAdBannerBinding

/**
 * The ad banner at the top of the file list (design: files-筛选).
 *
 * The slot shows the local sample artwork of the design until the ad of the
 * banner arrives.
 */
class AdBannerAdapter(
    private val content: AdContent,
) : RecyclerView.Adapter<AdBannerAdapter.BannerHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerHolder {
        val binding = FilesItemAdBannerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return BannerHolder(binding)
    }

    override fun onBindViewHolder(holder: AdBannerAdapter.BannerHolder, position: Int) {
        val slot = holder.binding.adSlot
        slot.style = AdSlotView.Style.BANNER
        // The banner of the design is a card with round corners in the list:
        // the icon sits 12dp from the left, the button 8dp from the right.
        slot.cornerRadiusDp = CORNER_RADIUS_DP
        slot.setPadding(dp(holder, 12), 0, dp(holder, 8), 0)
        slot.content = content
        Ads.loadBanner(slot)
    }

    override fun getItemCount(): Int = 1

    class BannerHolder(val binding: FilesItemAdBannerBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object {
        /** The round corners of the card of the design. */
        private const val CORNER_RADIUS_DP = 8
    }

    /** The dp of the screen of the holder. */
    private fun dp(holder: BannerHolder, value: Int): Int =
        (value * holder.itemView.resources.displayMetrics.density).toInt()
}
