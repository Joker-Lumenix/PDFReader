package com.peng.pdfreader.feature.guide

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.databinding.GuidePageBinding

/** Adapter of the guide pages inside the [androidx.viewpager2.widget.ViewPager2]. */
class GuidePageAdapter(
    private val pages: List<GuidePage>,
) : RecyclerView.Adapter<GuidePageAdapter.PageHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val binding = GuidePageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PageHolder(binding)
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        val page = pages[position]
        val context = holder.itemView.context
        holder.binding.ivIllustration.setImageResource(page.imageRes)
        holder.binding.tvTitle.setText(page.titleRes)
        holder.binding.tvSubtitle.setText(page.subtitleRes)
    }

    override fun getItemCount(): Int = pages.size

    class PageHolder(val binding: GuidePageBinding) : RecyclerView.ViewHolder(binding.root)
}
