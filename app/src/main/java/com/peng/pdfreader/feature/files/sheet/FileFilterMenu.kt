package com.peng.pdfreader.feature.files.sheet

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.peng.pdfreader.R
import com.peng.pdfreader.data.model.DocType
import com.peng.pdfreader.data.repository.DocumentRepository
import com.peng.pdfreader.databinding.FilesPopupFilterBinding

/**
 * The type filter popover (design: files-筛选).
 *
 * A small card that drops below the title bar and lists the file types with
 * the number of documents behind them; the active type carries the radio. The
 * popover is not a dialog - it floats above the list and closes as soon as a
 * type is picked.
 */
object FileFilterMenu {

    /** Width of the card (design: 125dp). */
    private const val WIDTH_DP = 125

    /** Distance of the card below the top of the page (design: 136dp). */
    private const val TOP_DP = 136

    /**
     * Shows the popover below [anchor].
     *
     * @param page the root view of the page, the card is placed against it
     * @param anchor the action that opened the popover
     * @param current the type that is currently active, `null` means "all"
     * @param onSelected called with the picked type, `null` means "all"
     */
    fun show(
        page: View,
        anchor: View,
        current: DocType?,
        onSelected: (DocType?) -> Unit,
    ) {
        val context = anchor.context
        val binding = FilesPopupFilterBinding.inflate(LayoutInflater.from(context))
        val counts = DocumentRepository.get(context).all()
            .groupingBy { it.type }
            .eachCount()
        val total = counts.values.sum()

        val width = dp(context, WIDTH_DP)
        val popup = PopupWindow(
            binding.root,
            width,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false,
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            isFocusable = false
        }

        // Picking a type closes the popover and applies the filter.
        val pick: (DocType?) -> Unit = { type ->
            popup.dismiss()
            onSelected(type)
        }

        bind(
            binding.rowAll, binding.tvAll, binding.radioAll,
            null, total, current, pick,
        )
        bind(
            binding.rowPdf, binding.tvPdf, binding.radioPdf,
            DocType.PDF, counts[DocType.PDF] ?: 0, current, pick,
        )
        bind(
            binding.rowWord, binding.tvWord, binding.radioWord,
            DocType.WORD, counts[DocType.WORD] ?: 0, current, pick,
        )
        bind(
            binding.rowExcel, binding.tvExcel, binding.radioExcel,
            DocType.EXCEL, counts[DocType.EXCEL] ?: 0, current, pick,
        )
        bind(
            binding.rowPpt, binding.tvPpt, binding.radioPpt,
            DocType.PPT, counts[DocType.PPT] ?: 0, current, pick,
        )

        // The card is right aligned with the anchor and hangs below the header.
        val anchorLocation = IntArray(2)
        anchor.getLocationInWindow(anchorLocation)
        val pageLocation = IntArray(2)
        page.getLocationInWindow(pageLocation)
        val top = pageLocation[1] + dp(context, TOP_DP)
        val yOffset = top - (anchorLocation[1] + anchor.height)
        val xOffset = anchor.width - width
        popup.showAsDropDown(anchor, xOffset, yOffset)
    }

    private fun bind(
        row: View,
        label: TextView,
        radio: ImageView,
        type: DocType?,
        count: Int,
        current: DocType?,
        onSelected: (DocType?) -> Unit,
    ) {
        val selected = type == current
        val name = if (type == null) {
            label.context.getString(R.string.all)
        } else {
            label.context.getString(type.labelRes)
        }
        label.text = "$name($count)"
        label.setTextColor(
            ContextCompat.getColor(
                label.context,
                if (selected) R.color.brand_primary else R.color.text_secondary,
            ),
        )
        radio.setImageResource(
            if (selected) {
                R.mipmap.ic_checkbox_circle_select
            } else {
                R.mipmap.ic_checkbox_circle_unselect
            },
        )
        row.setOnClickListener { onSelected(type) }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
