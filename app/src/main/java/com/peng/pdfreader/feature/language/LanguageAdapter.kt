package com.peng.pdfreader.feature.language

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.R
import com.peng.pdfreader.databinding.LanguageRowBinding

/**
 * The single choice list of the language page.
 *
 * The radio button is drawn in code: the design shows an 18dp circle that is
 * an empty ring when unselected and a filled brand circle when selected.
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
        holder.binding.tvLanguage.text = context.getString(option.labelRes)
        holder.binding.vRadio.background = radioDrawable(context, position == selectedIndex)
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

    companion object {
        private const val RADIO_SIZE_DP = 18

        fun radioDrawable(context: Context, selected: Boolean): Drawable {
            val density = context.resources.displayMetrics.density
            val size = (RADIO_SIZE_DP * density).toInt()
            return GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (selected) {
                    setColor(ContextCompat.getColor(context, R.color.brand_primary))
                } else {
                    setColor(Color.TRANSPARENT)
                    setStroke(
                        (density).toInt().coerceAtLeast(1),
                        ContextCompat.getColor(context, R.color.surface_border_strong),
                    )
                }
            }.apply { setSize(size, size) }
        }
    }

    class OptionHolder(val binding: LanguageRowBinding) : RecyclerView.ViewHolder(binding.root)
}
