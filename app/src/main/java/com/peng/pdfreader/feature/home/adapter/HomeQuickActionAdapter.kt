package com.peng.pdfreader.feature.home.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.R
import com.peng.pdfreader.databinding.HomeItemQuickActionBinding
import com.peng.pdfreader.databinding.HomeItemQuickMoreBinding
import com.peng.pdfreader.feature.home.model.QuickAction

/**
 * Quick action row of the home tab.
 *
 * Two wide tool cards plus the narrow chevron card that opens the full tool
 * list. The row is wider than the screen, so it is used inside a horizontally
 * scrolling list.
 */
class HomeQuickActionAdapter(
    private val onAction: (QuickAction) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemCount(): Int = ENTRIES.size

    override fun getItemViewType(position: Int): Int = position

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val entry = ENTRIES[viewType]
        return if (entry.action == QuickAction.MORE) {
            MoreHolder(HomeItemQuickMoreBinding.inflate(inflater, parent, false), entry)
        } else {
            ActionHolder(HomeItemQuickActionBinding.inflate(inflater, parent, false), entry)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ActionHolder -> holder.bind()
            is MoreHolder -> holder.bind()
        }
    }

    private inner class ActionHolder(
        private val binding: HomeItemQuickActionBinding,
        private val entry: Entry,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            binding.quickIcon.setImageResource(entry.iconRes)
            binding.quickLabel.setText(entry.labelRes)
            binding.root.contentDescription = binding.root.context.getString(entry.labelRes)
            binding.root.setOnClickListener { onAction(entry.action) }
        }
    }

    private inner class MoreHolder(
        private val binding: HomeItemQuickMoreBinding,
        private val entry: Entry,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            binding.root.contentDescription = binding.root.context.getString(entry.labelRes)
            binding.root.setOnClickListener { onAction(entry.action) }
        }
    }

    /** One card of the row. */
    private class Entry(
        val action: QuickAction,
        @StringRes val labelRes: Int,
        @DrawableRes val iconRes: Int,
    )

    companion object {
        private val ENTRIES: List<Entry> = listOf(
            Entry(
                action = QuickAction.IMAGE_TO_PDF,
                labelRes = R.string.home_quick_image_to_pdf,
                iconRes = R.mipmap.ic_tools_image_to_pdf,
            ),
            Entry(
                action = QuickAction.MERGE_PDF,
                labelRes = R.string.home_quick_merge_pdf,
                iconRes = R.mipmap.ic_tools_merge_pdf,
            ),
            Entry(
                action = QuickAction.MORE,
                labelRes = R.string.home_quick_more,
                iconRes = 0,
            ),
        )
    }
}
