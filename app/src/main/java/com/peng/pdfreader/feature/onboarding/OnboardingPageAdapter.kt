package com.peng.pdfreader.feature.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.databinding.OnboardingPageBinding

/** Adapter of the onboarding pages inside the [androidx.viewpager2.widget.ViewPager2]. */
class OnboardingPageAdapter(
    private val pages: List<OnboardingPage>,
) : RecyclerView.Adapter<OnboardingPageAdapter.PageHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val binding = OnboardingPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PageHolder(binding)
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        val page = pages[position]
        val context = holder.itemView.context
        holder.binding.ivIllustration.setImageResource(page.imageRes)
        holder.binding.tvTitle.text = context.getString(page.titleRes)
        holder.binding.tvSubtitle.text = context.getString(page.subtitleRes)
    }

    override fun getItemCount(): Int = pages.size

    class PageHolder(val binding: OnboardingPageBinding) : RecyclerView.ViewHolder(binding.root)
}
