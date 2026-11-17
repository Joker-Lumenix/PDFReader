package com.peng.pdfreader.ui.adapter.listAdapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.ads.Ads
import com.peng.pdfreader.appcore.widget.AdContent
import com.peng.pdfreader.appcore.widget.AdSlotView
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

    override fun onBindViewHolder(holder: BannerHolder, position: Int) {
        holder.binding.adSlot.style = AdSlotView.Style.BANNER
        holder.binding.adSlot.content = content
        Ads.loadBanner(holder.binding.adSlot)
    }

    override fun getItemCount(): Int = 1

    class BannerHolder(val binding: FilesItemAdBannerBinding) :
        RecyclerView.ViewHolder(binding.root)
}
