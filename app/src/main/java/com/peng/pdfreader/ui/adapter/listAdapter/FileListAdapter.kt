package com.peng.pdfreader.ui.adapter.listAdapter

import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.peng.pdfreader.R
import com.peng.pdfreader.appcore.widget.FileItemUiState
import com.peng.pdfreader.appcore.widget.FileListItemView
import com.peng.pdfreader.appcore.widget.createForList
import com.peng.pdfreader.data.Document

/**
 * The document list of the files tab.
 *
 * Every row is the one shared template
 * ([FileListItemView]); only the state that
 * is bound to it changes. The adapter is a [ListAdapter], so the list is
 * diffed and only the changed rows are rebound.
 */
class FileListAdapter(
    private val callbacks: Callbacks,
) : ListAdapter<FileListAdapter.Row, FileListAdapter.FileViewHolder>(DIFF) {

    /**
     * One row: the document plus the state that is rendered for it.
     *
     * [titleColor] overrides the colour of the name, the search hits paint it
     * in the light brand colour (design: files-搜索).
     */
    data class Row(
        val document: Document,
        val state: FileItemUiState,
        val titleColor: Int? = null,
    )

    /** Everything the list can report back to the page. */
    interface Callbacks {
        /** The row itself was tapped. */
        fun onRowClick(document: Document)

        /** The "more" affordance was tapped. */
        fun onRowMore(document: Document)

        /** The selection checkbox was tapped. */
        fun onRowChecked(document: Document, checked: Boolean)

        /** The row was long pressed - this enters the multi select mode. */
        fun onRowLongPressed(document: Document)
    }

    class FileViewHolder(
        private val item: FileListItemView,
    ) : RecyclerView.ViewHolder(item) {

        fun bind(row: Row, callbacks: Callbacks) {
            item.bind(row.state)
            applyTypeChipMetrics(item)
            applyTitleColor(item, row.titleColor)
            applyCheckboxIcon(item, row.state.checked)
            item.onClick = { callbacks.onRowClick(row.document) }
            item.onMoreClick = { callbacks.onRowMore(row.document) }
            item.onCheckedChange = { checked -> callbacks.onRowChecked(row.document, checked) }
            item.setOnLongClickListener {
                callbacks.onRowLongPressed(row.document)
                true
            }
        }

        /** A search hit paints the name in the light brand colour. */
        private fun applyTitleColor(item: FileListItemView, color: Int?) {
            val title = item.findViewById<TextView>(R.id.tvTitle) ?: return
            title.setTextColor(
                color ?: ContextCompat.getColor(item.context, R.color.text_secondary),
            )
        }

        /**
         * The selection marks the row with a circle, the shared widget ships a
         * square one (design: files-Selected).
         */
        private fun applyCheckboxIcon(item: FileListItemView, checked: Boolean) {
            val box = item.findViewById<ImageView>(R.id.ivCheckbox) ?: return
            box.setImageResource(
                if (checked) {
                    R.mipmap.ic_checkbox_circle_select
                } else {
                    R.mipmap.ic_checkbox_circle_unselect
                },
            )
        }

        /**
         * The design draws the type pill as a fixed 35x16 box that sits 4dp
         * away from the meta line; the shared widget wraps the label instead.
         */
        private fun applyTypeChipMetrics(item: FileListItemView) {
            val chip = item.findViewById<TextView>(R.id.tvType) ?: return
            val density = item.resources.displayMetrics.density
            val params = chip.layoutParams
            params.width = (TYPE_CHIP_WIDTH_DP * density).toInt()
            params.height = (TYPE_CHIP_HEIGHT_DP * density).toInt()
            (params as? ViewGroup.MarginLayoutParams)?.marginEnd = (TYPE_CHIP_GAP_DP * density).toInt()
            chip.layoutParams = params
            chip.setPadding(0, (TYPE_CHIP_PADDING_V_DP * density).toInt(), 0, (TYPE_CHIP_PADDING_V_DP * density).toInt())
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder =
        FileViewHolder(createForList(parent.context))

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position), callbacks)
    }

    companion object {
        private const val TYPE_CHIP_WIDTH_DP = 35
        private const val TYPE_CHIP_HEIGHT_DP = 16
        private const val TYPE_CHIP_GAP_DP = 4
        private const val TYPE_CHIP_PADDING_V_DP = 2

        private val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(oldItem: Row, newItem: Row): Boolean =
                oldItem.document.id == newItem.document.id

            override fun areContentsTheSame(oldItem: Row, newItem: Row): Boolean =
                oldItem.state == newItem.state && oldItem.titleColor == newItem.titleColor
        }
    }
}
