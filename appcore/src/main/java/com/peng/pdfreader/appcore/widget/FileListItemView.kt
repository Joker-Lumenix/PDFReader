package com.peng.pdfreader.appcore.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.peng.pdfreader.appcore.R

/**
 * The single file list row template.
 *
 * The design contains several list rows (normal document, multi select, search
 * hit, advertising row). They all share this one layout - only the visibility
 * of the individual sub widgets changes, driven by [FileItemUiState].
 */
class FileListItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val checkbox: android.widget.ImageView
    private val thumbnail: android.widget.ImageView
    private val title: TextView
    private val badge: TextView
    private val inlineAction: TextView
    private val typeChip: TextView
    private val meta: TextView
    private val more: android.widget.ImageView

    /** Raised when the row itself is tapped. */
    var onClick: (() -> Unit)? = null
    /** Raised when the "more" affordance is tapped. */
    var onMoreClick: (() -> Unit)? = null
    /** Raised when the selection checkbox is tapped. */
    var onCheckedChange: ((Boolean) -> Unit)? = null
    /** Raised when the inline action button is tapped. */
    var onInlineActionClick: (() -> Unit)? = null

    init {
        orientation = HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.widget_file_list_item, this, true)

        checkbox = findViewById(R.id.ivCheckbox)
        thumbnail = findViewById(R.id.ivThumbnail)
        title = findViewById(R.id.tvTitle)
        badge = findViewById(R.id.tvBadge)
        inlineAction = findViewById(R.id.tvInlineAction)
        typeChip = findViewById(R.id.tvType)
        meta = findViewById(R.id.tvMeta)
        more = findViewById(R.id.ivMore)

        setOnClickListener { onClick?.invoke() }
        more.setOnClickListener { onMoreClick?.invoke() }
        checkbox.setOnClickListener {
            val next = !isChecked
            isChecked = next
            onCheckedChange?.invoke(next)
        }
        inlineAction.setOnClickListener { onInlineActionClick?.invoke() }
    }

    /** Current selection state of the checkbox. */
    var isChecked: Boolean = false
        set(value) {
            field = value
            checkbox.setImageResource(
                if (value) R.drawable.ic_checkbox_square_select
                else R.drawable.ic_checkbox_square_unselect,
            )
        }

    /** Binds the row to a state snapshot. */
    fun bind(state: FileItemUiState) {
        title.text = state.name
        meta.text = state.meta.orEmpty()
        meta.visibility = if (state.meta.isNullOrEmpty()) GONE else VISIBLE

        typeChip.visibility = if (state.showType && !state.typeLabel.isNullOrEmpty()) VISIBLE else GONE
        state.typeLabel?.let { typeChip.text = it }
        state.typeColor?.let { typeChip.setTextColor(it) }

        thumbnail.visibility = if (state.showThumbnail) VISIBLE else INVISIBLE
        state.thumbnail?.let { thumbnail.setImageDrawable(it) }

        checkbox.visibility = if (state.showCheckbox) VISIBLE else GONE
        isChecked = state.checked

        more.visibility = if (state.showMore) VISIBLE else GONE

        badge.visibility = if (state.badge.isNullOrEmpty()) GONE else VISIBLE
        state.badge?.let { badge.text = it }

        inlineAction.visibility = if (state.inlineActionText.isNullOrEmpty()) GONE else VISIBLE
        state.inlineActionText?.let { inlineAction.text = it }
    }
}

/**
 * Everything a file row can show.
 *
 * @param name file name, 15sp / 600
 * @param meta secondary line, 12sp / 400 / #999999
 * @param typeLabel file type pill label, 10sp / 700
 * @param typeColor colour of the type label
 * @param thumbnail 48x48 thumbnail / file type logo
 * @param showType show the type pill
 * @param showMore show the "more" affordance
 * @param showCheckbox multi select mode
 * @param checked checkbox state
 * @param badge small coloured badge next to the title (advertising rows)
 * @param inlineAction inline pill button rendered next to the title
 */
data class FileItemUiState(
    val name: String,
    val meta: String? = null,
    val typeLabel: String? = null,
    val typeColor: Int? = null,
    val thumbnail: Drawable? = null,
    val showThumbnail: Boolean = true,
    val showType: Boolean = true,
    val showMore: Boolean = true,
    val showCheckbox: Boolean = false,
    val checked: Boolean = false,
    val badge: String? = null,
    val inlineActionText: String? = null,
)
