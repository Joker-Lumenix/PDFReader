package com.peng.pdfreader.feature.language

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.R
import com.peng.pdfreader.databinding.LanguageRowBinding

/**
 * The single choice list of the language page.
 *
 * The selected row of the design is the red gradient pill with white text and
 * a white radio with a red check mark, every other row is the plain endonym
 * with an empty ring.
 */
class LanguageAdapter(
    private val onSelected: (LanguageOption) -> Unit,
) : RecyclerView.Adapter<LanguageAdapter.OptionHolder>() {

    private var selectedIndex = RecyclerView.NO_POSITION

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionHolder {
        val binding = LanguageRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OptionHolder(binding)
    }

    override fun onBindViewHolder(holder: OptionHolder, position: Int) {
        val option = LanguageCatalog.options[position]
        val context = holder.itemView.context
        val selected = position == selectedIndex

        holder.binding.tvLanguage.text = context.getString(option.labelRes)
        holder.binding.tvLanguage.setTextColor(
            ContextCompat.getColor(
                context,
                if (selected) R.color.white else R.color.text_secondary,
            ),
        )
        holder.binding.vRadio.background = LanguageRadioDrawable.of(context, selected)
        // The design marks the selected row with a red gradient pill.
        holder.binding.vSelectedPill.visibility = if (selected) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onSelected(option) }
    }

    override fun getItemCount(): Int = LanguageCatalog.options.size

    /** Highlights the given option, `null` clears the selection. */
    fun select(option: LanguageOption?) {
        val previous = selectedIndex
        val index = option?.let { LanguageCatalog.options.indexOf(it) } ?: RecyclerView.NO_POSITION
        selectedIndex = if (index >= 0) index else RecyclerView.NO_POSITION
        if (previous != RecyclerView.NO_POSITION) notifyItemChanged(previous)
        if (selectedIndex != RecyclerView.NO_POSITION) notifyItemChanged(selectedIndex)
    }

    class OptionHolder(val binding: LanguageRowBinding) : RecyclerView.ViewHolder(binding.root)
}
